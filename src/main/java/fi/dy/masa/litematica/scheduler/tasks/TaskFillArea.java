package fi.dy.masa.litematica.scheduler.tasks;

import java.util.Collection;
import java.util.List;
import java.util.Queue;
import java.util.function.BiConsumer;
import javax.annotation.Nullable;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.BlockPos;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import com.google.common.collect.Queues;
import fi.dy.masa.malilib.gui.Message.MessageType;
import fi.dy.masa.malilib.util.InfoUtils;
import fi.dy.masa.malilib.util.IntBoundingBox;
import fi.dy.masa.malilib.util.LayerMode;
import fi.dy.masa.malilib.util.LayerRange;
import fi.dy.masa.litematica.config.Configs;
import fi.dy.masa.litematica.data.DataManager;
import fi.dy.masa.litematica.render.infohud.InfoHud;
import fi.dy.masa.litematica.selection.Box;
import fi.dy.masa.litematica.util.EntityUtils;
import fi.dy.masa.litematica.util.WorldUtils;

public class TaskFillArea extends TaskProcessChunkMultiPhase {
    protected final Queue<String> queuedCommands = Queues.newArrayDeque();
    protected final BlockState fillState;

    @Nullable
    protected final BlockState replaceState;

    protected final String fillCommand;
    protected final String blockString;
    protected final int maxBoxVolume;
    protected final boolean removeEntities;

    public TaskFillArea(
            List<Box> boxes, BlockState fillState, @Nullable BlockState replaceState, boolean removeEntities) {
        this(boxes, fillState, replaceState, removeEntities, "litematica.gui.label.task_name.fill");
    }

    protected TaskFillArea(
            List<Box> boxes,
            BlockState fillState,
            @Nullable BlockState replaceState,
            boolean removeEntities,
            String nameOnHud) {
        super(nameOnHud);

        this.fillState = fillState;
        this.replaceState = replaceState;
        this.removeEntities = removeEntities;
        this.maxBoxVolume = Configs.Generic.COMMAND_FILL_MAX_VOLUME.getIntegerValue();
        this.maxCommandsPerTick = Configs.Generic.COMMAND_LIMIT.getIntegerValue();
        this.fillCommand = Configs.Generic.COMMAND_NAME_FILL.getStringValue();

        String blockString = BlockStateParser.serialize(fillState);

        if (replaceState != null) {
            blockString += " replace " + BlockStateParser.serialize(replaceState);
        }

        this.blockString = blockString;
        this.processBoxBlocksTask = this::sendQueuedCommands;

        if (Configs.Generic.COMMAND_FILL_NO_CHUNK_CLAMP.getBooleanValue()) {
            this.addNonChunkClampedBoxes(boxes);
        } else {
            this.addPerChunkBoxes(boxes);
        }
    }

    @Override
    public boolean canExecute() {
        return super.canExecute() && this.blockString != null;
    }

    @Override
    protected boolean canProcessChunk(ChunkPos pos) {
        return this.areSurroundingChunksLoaded(pos, this.clientWorld, 0);
    }

    @Override
    public void init() {
        super.init();

        if (this.useWorldEdit && this.isInWorld()) {
            this.sendCommand("/perf neighbors off");
        }
    }

    @Override
    public boolean execute() {
        return this.executeMultiPhase();
    }

    @Override
    protected void onNextChunkFetched(ChunkPos pos) {
        if (this.isClientWorld) {
            this.queueCommandsForBoxesInChunk(pos);
        } else {
            this.directFillBoxesInChunk(pos);
        }
    }

    protected void addNonChunkClampedBoxes(Collection<Box> allBoxes) {
        this.addNonChunkClampedBoxes(allBoxes, new LayerRange(null));
    }

    protected void addNonChunkClampedBoxes(Collection<Box> allBoxes, LayerRange range) {
        this.boxesInChunks.clear();
        this.pendingChunks.clear();

        if (range.getLayerMode() == LayerMode.ALL) {
            addBoxes(allBoxes, this::clampToWorldHeightAndAddBox);
        } else {
            getLayerRangeClampedBoxes(allBoxes, range, this::clampToWorldHeightAndAddBox);
        }

        this.pendingChunks.addAll(this.boxesInChunks.keySet());

        this.sortChunkList();
    }

    protected static void addBoxes(Collection<Box> boxes, BiConsumer<ChunkPos, IntBoundingBox> consumer) {
        for (Box box : boxes) {
            int boxMinX = Math.min(box.getPos1().getX(), box.getPos2().getX());
            int boxMinY = Math.min(box.getPos1().getY(), box.getPos2().getY());
            int boxMinZ = Math.min(box.getPos1().getZ(), box.getPos2().getZ());
            int boxMaxX = Math.max(box.getPos1().getX(), box.getPos2().getX());
            int boxMaxY = Math.max(box.getPos1().getY(), box.getPos2().getY());
            int boxMaxZ = Math.max(box.getPos1().getZ(), box.getPos2().getZ());

            consumer.accept(
                    new ChunkPos(boxMinX >> 4, boxMinZ >> 4),
                    new IntBoundingBox(boxMinX, boxMinY, boxMinZ, boxMaxX, boxMaxY, boxMaxZ));
        }
    }

    protected static void getLayerRangeClampedBoxes(
            Collection<Box> boxes, LayerRange range, BiConsumer<ChunkPos, IntBoundingBox> consumer) {
        for (Box box : boxes) {
            final int rangeMin = range.getLayerMin();
            final int rangeMax = range.getLayerMax();
            int boxMinX = Math.min(box.getPos1().getX(), box.getPos2().getX());
            int boxMinY = Math.min(box.getPos1().getY(), box.getPos2().getY());
            int boxMinZ = Math.min(box.getPos1().getZ(), box.getPos2().getZ());
            int boxMaxX = Math.max(box.getPos1().getX(), box.getPos2().getX());
            int boxMaxY = Math.max(box.getPos1().getY(), box.getPos2().getY());
            int boxMaxZ = Math.max(box.getPos1().getZ(), box.getPos2().getZ());

            switch (range.getAxis()) {
                case X:
                    if (rangeMax < boxMinX || rangeMin > boxMaxX) {
                        continue;
                    }
                    boxMinX = Math.max(boxMinX, rangeMin);
                    boxMaxX = Math.min(boxMaxX, rangeMax);
                    break;
                case Y:
                    if (rangeMax < boxMinY || rangeMin > boxMaxY) {
                        continue;
                    }
                    boxMinY = Math.max(boxMinY, rangeMin);
                    boxMaxY = Math.min(boxMaxY, rangeMax);
                    break;
                case Z:
                    if (rangeMax < boxMinZ || rangeMin > boxMaxZ) {
                        continue;
                    }
                    boxMinZ = Math.max(boxMinZ, rangeMin);
                    boxMaxZ = Math.min(boxMaxZ, rangeMax);
                    break;
            }

            consumer.accept(
                    new ChunkPos(boxMinX >> 4, boxMinZ >> 4),
                    new IntBoundingBox(boxMinX, boxMinY, boxMinZ, boxMaxX, boxMaxY, boxMaxZ));
        }
    }

    protected void queueCommandsForBoxesInChunk(ChunkPos pos) {
        for (IntBoundingBox box : this.getBoxesInChunk(pos)) {
            this.queueFillCommandsForBox(box, this.removeEntities);
        }

        // Use this phase to send the queued commands
        this.phase = TaskPhase.PROCESS_BOX_BLOCKS;
    }

    protected void directFillBoxesInChunk(ChunkPos pos) {
        for (IntBoundingBox box : this.getBoxesInChunk(pos)) {
            this.fillBoxDirect(box, this.removeEntities);
        }

        this.finishProcessingChunk(pos);
    }

    protected void sendQueuedCommands() {
        while (this.sentCommandsThisTick < this.maxCommandsPerTick && !this.queuedCommands.isEmpty()) {
            this.sendCommand(this.queuedCommands.poll());
        }

        if (this.queuedCommands.isEmpty()) {
            this.finishProcessingChunk(this.currentChunkPos);
        }
    }

    protected void fillBoxDirect(IntBoundingBox box, boolean removeEntities) {
        if (removeEntities) {
            net.minecraft.world.phys.AABB aabb = new net.minecraft.world.phys.AABB(
                    box.minX, box.minY, box.minZ, box.maxX + 1, box.maxY + 1, box.maxZ + 1);
            List<Entity> entities = this.world.getEntities(this.mc.player, aabb, EntityUtils.NOT_PLAYER);

            for (Entity entity : entities) {
                if (!(entity instanceof Player)) {
                    entity.discard();
                }
            }
        }

        WorldUtils.setShouldPreventBlockUpdates(this.world, true);

        BlockState barrier = Blocks.BARRIER.defaultBlockState();
        BlockPos.MutableBlockPos posMutable = new BlockPos.MutableBlockPos();

        for (int z = box.minZ; z <= box.maxZ; ++z) {
            for (int x = box.minX; x <= box.maxX; ++x) {
                for (int y = box.maxY; y >= box.minY; --y) {
                    posMutable.set(x, y, z);
                    BlockState oldState = this.world.getBlockState(posMutable);

                    if ((this.replaceState == null && oldState != this.fillState) || oldState == this.replaceState) {
                        BlockEntity te = this.world.getBlockEntity(posMutable);

                        if (te instanceof Container) {
                            ((Container) te).clearContent();
                            this.world.setBlock(posMutable, barrier, 0x32);
                        }

                        this.world.setBlock(posMutable, this.fillState, 0x32);
                    }
                }
            }
        }

        WorldUtils.setShouldPreventBlockUpdates(this.world, false);
    }

    protected void queueFillCommandsForBox(IntBoundingBox box, boolean removeEntities) {
        if (removeEntities) {
            net.minecraft.world.phys.AABB aabb = new net.minecraft.world.phys.AABB(
                    box.minX, box.minY, box.minZ, box.maxX + 1, box.maxY + 1, box.maxZ + 1);

            if (!this.world
		                 .getEntities(this.mc.player, aabb, EntityUtils.NOT_PLAYER).isEmpty()) {
                String killCmd = String.format(
                        "kill @e[type=!player,x=%d,y=%d,z=%d,dx=%d,dy=%d,dz=%d]",
                        box.minX,
                        box.minY,
                        box.minZ,
                        box.maxX - box.minX + 1,
                        box.maxY - box.minY + 1,
                        box.maxZ - box.minZ + 1);

                this.queuedCommands.offer(killCmd);
            }
        }

        int totalVolume = (box.maxX - box.minX + 1) * (box.maxY - box.minY + 1) * (box.maxZ - box.minZ + 1);

        if (totalVolume <= this.maxBoxVolume || this.useWorldEdit) {
            this.queueFillCommandForBox(box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ);
        } else {
            int singleLayerVolume = (box.maxX - box.minX + 1) * (box.maxZ - box.minZ + 1);
            int singleBoxHeight = this.maxBoxVolume / singleLayerVolume;

            if (singleBoxHeight < 1) {
                InfoUtils.showGuiOrInGameMessage(
                        MessageType.ERROR, "Error: Calculated single box height was less than 1 block");
                return;
            }

            for (int y = box.minY; y <= box.maxY; y += singleBoxHeight) {
                int maxY = Math.min(y + singleBoxHeight - 1, box.maxY);
                this.queueFillCommandForBox(box.minX, y, box.minZ, box.maxX, maxY, box.maxZ);
            }
        }
    }

    protected void queueFillCommandForBox(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        if (this.useWorldEdit) {
            this.queuedCommands.offer(String.format("/pos1 %d,%d,%d", minX, minY, minZ));
            this.queuedCommands.offer(String.format("/pos2 %d,%d,%d", maxX, maxY, maxZ));
            this.queuedCommands.offer("/set " + this.blockString);
        } else {
            String fillCmd = String.format(
                    "%s %d %d %d %d %d %d %s", this.fillCommand, minX, minY, minZ, maxX, maxY, maxZ, this.blockString);
            this.queuedCommands.offer(fillCmd);
        }
    }

    @Override
    protected void onStop() {
        this.printCompletionMessage();
        this.sendTaskEndCommands();

        DataManager.removeChatListener(this.gameRuleListener);
        InfoHud.getInstance().removeInfoHudRenderer(this, false);

        super.onStop();
    }

    protected void printCompletionMessage() {
        if (this.finished) {
            if (this.printCompletionMessage) {
                InfoUtils.showGuiMessage(MessageType.SUCCESS, "litematica.message.area_filled");
            }
        } else {
            InfoUtils.showGuiMessage(MessageType.ERROR, "litematica.message.area_fill_fail");
        }
    }
}
