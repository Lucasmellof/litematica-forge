package fi.dy.masa.litematica.render.schematic;

import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.ThreadFactory;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.phys.Vec3;
import com.google.common.collect.Queues;
import com.google.common.primitives.Doubles;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListenableFutureTask;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.VertexBuffer;
import org.apache.logging.log4j.Logger;
import fi.dy.masa.litematica.Litematica;
import fi.dy.masa.litematica.render.schematic.ChunkRendererSchematicVbo.OverlayRenderType;

public class ChunkRenderDispatcherLitematica {
    private static final Logger LOGGER = Litematica.logger;
    private static final ThreadFactory THREAD_FACTORY = (new ThreadFactoryBuilder())
            .setNameFormat("Litematica Chunk Batcher %d")
            .setDaemon(true)
            .build();

    private final List<Thread> listWorkerThreads = new ArrayList<>();
    private final List<ChunkRenderWorkerLitematica> listThreadedWorkers = new ArrayList<>();
    private final PriorityBlockingQueue<ChunkRenderTaskSchematic> queueChunkUpdates = Queues.newPriorityBlockingQueue();
    private final BlockingQueue<BufferBuilderCache> queueFreeRenderBuilders;
    private final Queue<ChunkRenderDispatcherLitematica.PendingUpload> queueChunkUploads = Queues.newPriorityQueue();
    private final ChunkRenderWorkerLitematica renderWorker;
    private final int countRenderBuilders;
    private Vec3 cameraPos;

    public ChunkRenderDispatcherLitematica() {
        // TODO/FIXME 1.17
        // int threadLimitMemory = Math.max(1, (int)((double)Runtime.getRuntime().maxMemory() * 0.3D) / 10485760);
        // int threadLimitCPU = Math.max(1, MathHelper.clamp(Runtime.getRuntime().availableProcessors(), 1,
        // threadLimitMemory / 5));
        // this.countRenderBuilders = MathHelper.clamp(threadLimitCPU * 10, 1, threadLimitMemory);
        this.countRenderBuilders = 2;
        this.cameraPos = Vec3.ZERO;

        /*
        if (threadLimitCPU > 1)
        {
            Litematica.logger.info("Creating {} render threads", threadLimitCPU);

            for (int i = 0; i < threadLimitCPU; ++i)
            {
                ChunkRenderWorkerLitematica worker = new ChunkRenderWorkerLitematica(this);
                Thread thread = THREAD_FACTORY.newThread(worker);
                thread.start();
                this.listThreadedWorkers.add(worker);
                this.listWorkerThreads.add(thread);
            }
        }
        */

        Litematica.logger.info("Using {} total BufferBuilder caches", this.countRenderBuilders + 1);

        this.queueFreeRenderBuilders = Queues.newArrayBlockingQueue(this.countRenderBuilders);

        for (int i = 0; i < this.countRenderBuilders; ++i) {
            this.queueFreeRenderBuilders.add(new BufferBuilderCache());
        }

        this.renderWorker = new ChunkRenderWorkerLitematica(this, new BufferBuilderCache());
    }

    public void setCameraPosition(Vec3 cameraPos) {
        this.cameraPos = cameraPos;
    }

    public Vec3 getCameraPos() {
        return this.cameraPos;
    }

    public String getDebugInfo() {
        return this.listWorkerThreads.isEmpty()
                ? String.format("pC: %03d, single-threaded", this.queueChunkUpdates.size())
                : String.format(
                        "pC: %03d, pU: %1d, aB: %1d",
                        this.queueChunkUpdates.size(),
                        this.queueChunkUploads.size(),
                        this.queueFreeRenderBuilders.size());
    }

    public boolean runChunkUploads(long finishTimeNano) {
        boolean ranTasks = false;

        while (true) {
            boolean processedTask = false;

            if (this.listWorkerThreads.isEmpty()) {
                ChunkRenderTaskSchematic generator = this.queueChunkUpdates.poll();

                if (generator != null) {
                    try {
                        this.renderWorker.processTask(generator);
                        processedTask = true;
                    } catch (InterruptedException var8) {
                        LOGGER.warn("Skipped task due to interrupt");
                    }
                }
            }

            synchronized (this.queueChunkUploads) {
                if (!this.queueChunkUploads.isEmpty()) {
                    (this.queueChunkUploads.poll()).uploadTask.run();
                    processedTask = true;
                    ranTasks = true;
                }
            }

            if (finishTimeNano == 0L || !processedTask || finishTimeNano < System.nanoTime()) {
                break;
            }
        }

        return ranTasks;
    }

    public boolean updateChunkLater(ChunkRendererSchematicVbo renderChunk) {
        // if (GuiBase.isCtrlDown()) System.out.printf("updateChunkLater()\n");
        renderChunk.getLockCompileTask().lock();
        boolean flag1;

        try {
            final ChunkRenderTaskSchematic generator = renderChunk.makeCompileTaskChunkSchematic(this::getCameraPos);

            generator.addFinishRunnable(() -> ChunkRenderDispatcherLitematica.this.queueChunkUpdates.remove(generator));

            boolean flag = this.queueChunkUpdates.offer(generator);

            if (!flag) {
                generator.finish();
            }

            flag1 = flag;
        } finally {
            renderChunk.getLockCompileTask().unlock();
        }

        return flag1;
    }

    public boolean updateChunkNow(ChunkRendererSchematicVbo chunkRenderer) {
        // if (GuiBase.isCtrlDown()) System.out.printf("updateChunkNow()\n");
        chunkRenderer.getLockCompileTask().lock();
        boolean flag;

        try {
            ChunkRenderTaskSchematic generator = chunkRenderer.makeCompileTaskChunkSchematic(this::getCameraPos);

            try {
                this.renderWorker.processTask(generator);
            } catch (InterruptedException ignored) {
            }

            flag = true;
        } finally {
            chunkRenderer.getLockCompileTask().unlock();
        }

        return flag;
    }

    public void stopChunkUpdates() {
        this.clearChunkUpdates();
        List<BufferBuilderCache> list = new ArrayList<>();

        while (list.size() != this.countRenderBuilders) {
            this.runChunkUploads(Long.MAX_VALUE);

            try {
                list.add(this.allocateRenderBuilder());
            } catch (InterruptedException ignored) {
            }
        }

        this.queueFreeRenderBuilders.addAll(list);
    }

    public void freeRenderBuilder(BufferBuilderCache builderCache) {
        this.queueFreeRenderBuilders.add(builderCache);
    }

    public BufferBuilderCache allocateRenderBuilder() throws InterruptedException {
        return this.queueFreeRenderBuilders.take();
    }

    public ChunkRenderTaskSchematic getNextChunkUpdate() throws InterruptedException {
        return this.queueChunkUpdates.take();
    }

    public boolean updateTransparencyLater(ChunkRendererSchematicVbo renderChunk) {
        // if (GuiBase.isCtrlDown()) System.out.printf("updateTransparencyLater()\n");
        renderChunk.getLockCompileTask().lock();
        boolean flag;

        try {
            final ChunkRenderTaskSchematic generator =
                    renderChunk.makeCompileTaskTransparencySchematic(this::getCameraPos);

            if (generator == null) {
                return true;
            }

            generator.addFinishRunnable(() -> ChunkRenderDispatcherLitematica.this.queueChunkUpdates.remove(generator));

            flag = this.queueChunkUpdates.offer(generator);
        } finally {
            renderChunk.getLockCompileTask().unlock();
        }

        return flag;
    }

    public ListenableFuture<Object> uploadChunkBlocks(
            final RenderType layer,
            final BufferBuilder buffer,
            final ChunkRendererSchematicVbo renderChunk,
            final ChunkRenderDataSchematic chunkRenderData,
            final double distanceSq) {
        if (Minecraft.getInstance().isSameThread()) {
            // if (GuiBase.isCtrlDown()) System.out.printf("uploadChunkBlocks()\n");
            this.uploadVertexBuffer(buffer, renderChunk.getBlocksVertexBufferByLayer(layer));
            return Futures.immediateFuture(null);
        } else {
            ListenableFutureTask<Object> futureTask = ListenableFutureTask.create(
		            () -> ChunkRenderDispatcherLitematica.this.uploadChunkBlocks(
		                    layer, buffer, renderChunk, chunkRenderData, distanceSq),
                    null);

            synchronized (this.queueChunkUploads) {
                this.queueChunkUploads.add(new ChunkRenderDispatcherLitematica.PendingUpload(futureTask, distanceSq));
                return futureTask;
            }
        }
    }

    public ListenableFuture<Object> uploadChunkOverlay(
            final OverlayRenderType type,
            final BufferBuilder buffer,
            final ChunkRendererSchematicVbo renderChunk,
            final ChunkRenderDataSchematic compiledChunk,
            final double distanceSq) {
        if (Minecraft.getInstance().isSameThread()) {
            // if (GuiBase.isCtrlDown()) System.out.printf("uploadChunkOverlay()\n");
            this.uploadVertexBuffer(buffer, renderChunk.getOverlayVertexBuffer(type));
            return Futures.immediateFuture(null);
        } else {
            ListenableFutureTask<Object> futureTask = ListenableFutureTask.create(
		            () -> ChunkRenderDispatcherLitematica.this.uploadChunkOverlay(
		                    type, buffer, renderChunk, compiledChunk, distanceSq),
                    null);

            synchronized (this.queueChunkUploads) {
                this.queueChunkUploads.add(new ChunkRenderDispatcherLitematica.PendingUpload(futureTask, distanceSq));
                return futureTask;
            }
        }
    }

    private void uploadVertexBuffer(BufferBuilder buffer, VertexBuffer vertexBuffer) {
        BufferBuilder.RenderedBuffer renderBuffer;

        if (buffer
                instanceof
                OmegaHackfixForCrashJustTemporarilyForNowISwearBecauseOfShittyBrokenCodeBufferBuilder
                compatBuffer) {
            if (compatBuffer.lastRenderBuildBuffer != null) {
                renderBuffer = compatBuffer.lastRenderBuildBuffer;
            } else {
                renderBuffer = compatBuffer.end();
            }
        } else {
            renderBuffer = buffer.end();
        }

        vertexBuffer.bind();
        vertexBuffer.upload(renderBuffer);
        VertexBuffer.unbind();
    }

    public void clearChunkUpdates() {
        while (!this.queueChunkUpdates.isEmpty()) {
            ChunkRenderTaskSchematic generator = this.queueChunkUpdates.poll();

            if (generator != null) {
                generator.finish();
            }
        }
    }

    public boolean hasChunkUpdates() {
        return this.queueChunkUpdates.isEmpty() && this.queueChunkUploads.isEmpty();
    }

    public void stopWorkerThreads() {
        this.clearChunkUpdates();

        for (ChunkRenderWorkerLitematica worker : this.listThreadedWorkers) {
            worker.notifyToStop();
        }

        for (Thread thread : this.listWorkerThreads) {
            try {
                thread.interrupt();
                thread.join();
            } catch (InterruptedException interruptedexception) {
                LOGGER.warn("Interrupted whilst waiting for worker to die", interruptedexception);
            }
        }

        this.queueFreeRenderBuilders.clear();
    }

    public boolean hasNoFreeRenderBuilders() {
        return this.queueFreeRenderBuilders.isEmpty();
    }

    public static class PendingUpload implements Comparable<ChunkRenderDispatcherLitematica.PendingUpload> {
        private final ListenableFutureTask<Object> uploadTask;
        private final double distanceSq;

        public PendingUpload(ListenableFutureTask<Object> uploadTaskIn, double distanceSqIn) {
            this.uploadTask = uploadTaskIn;
            this.distanceSq = distanceSqIn;
        }

        public int compareTo(ChunkRenderDispatcherLitematica.PendingUpload other) {
            return Doubles.compare(this.distanceSq, other.distanceSq);
        }
    }
}
