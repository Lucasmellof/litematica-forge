package fi.dy.masa.litematica.scheduler.tasks;

import java.util.ArrayList;
import java.util.Collection;
import com.google.common.collect.ArrayListMultimap;
import net.minecraft.Util;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.ChunkPos;
import fi.dy.masa.litematica.render.infohud.InfoHud;
import fi.dy.masa.litematica.schematic.placement.SchematicPlacement;
import fi.dy.masa.litematica.util.SchematicPlacingUtils;
import fi.dy.masa.malilib.gui.Message.MessageType;
import fi.dy.masa.malilib.util.InfoUtils;
import fi.dy.masa.malilib.util.LayerRange;

public class TaskPasteSchematicPerChunkDirect extends TaskPasteSchematicPerChunkBase {
    private final ArrayListMultimap<ChunkPos, SchematicPlacement> placementsPerChunk = ArrayListMultimap.create();

    public TaskPasteSchematicPerChunkDirect(
            Collection<SchematicPlacement> placements, LayerRange range, boolean changedBlocksOnly) {
        super(placements, range, changedBlocksOnly);
    }

    @Override
    public boolean canExecute() {
        return super.canExecute()
                && this.mc.hasSingleplayerServer()
                && this.world != null
                && !this.world.isClientSide;
    }

    @Override
    protected void onChunkAddedForHandling(ChunkPos pos, SchematicPlacement placement) {
        super.onChunkAddedForHandling(pos, placement);

        this.placementsPerChunk.put(pos, placement);
    }

    @Override
    public boolean execute() {
        // Nothing to do
        if (this.ignoreBlocks && this.ignoreEntities) {
            return true;
        }

        MinecraftServer server = this.mc.getSingleplayerServer();
        final long vanillaTickTime = server.tickTimes[server.getTickCount() % 100];
        final long timeStart = Util.getNanos();

        this.sortChunkList();

        for (int chunkIndex = 0; chunkIndex < this.pendingChunks.size(); ++chunkIndex) {
            long currentTime = Util.getNanos();
            long elapsedTickTime = vanillaTickTime + (currentTime - timeStart);

            if (elapsedTickTime >= 60000000L) {
                break;
            }

            ChunkPos pos = this.pendingChunks.get(chunkIndex);

            if (this.canProcessChunk(pos) && this.processChunk(pos)) {
                this.pendingChunks.remove(chunkIndex);
                --chunkIndex;
            }
        }

        if (this.pendingChunks.isEmpty()) {
            this.finished = true;
            return true;
        }

        this.updateInfoHudLines();

        return false;
    }

    @Override
    protected boolean processChunk(ChunkPos pos) {
        // TODO ignoreBlocks and ignoreEntities

        // New list to avoid CME
        ArrayList<SchematicPlacement> placements = new ArrayList<>(this.placementsPerChunk.get(pos));

        for (SchematicPlacement placement : placements) {
            if (SchematicPlacingUtils.placeToWorldWithinChunk(this.world, pos, placement, this.replace, false)) {
                this.placementsPerChunk.remove(pos, placement);
            }
        }

        return !this.placementsPerChunk.containsKey(pos);
    }

    @Override
    protected void onStop() {
        if (this.finished) {
            InfoUtils.showGuiOrActionBarMessage(MessageType.SUCCESS, "litematica.message.schematic_pasted");
        } else {
            InfoUtils.showGuiOrActionBarMessage(MessageType.ERROR, "litematica.message.error.schematic_paste_failed");
        }

        InfoHud.getInstance().removeInfoHudRenderer(this, false);

        super.onStop();
    }
}
