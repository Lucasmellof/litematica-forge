package fi.dy.masa.litematica.render.schematic;

import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import fi.dy.masa.litematica.world.WorldSchematic;

public class ChunkRenderDispatcherSchematic
{
    protected final WorldRendererSchematic renderer;
    protected final WorldSchematic world;
    protected int sizeY;
    protected int sizeX;
    protected int sizeZ;
    public ChunkRendererSchematicVbo[] renderers;

    public ChunkRenderDispatcherSchematic(WorldSchematic world, int viewDistance,
            WorldRendererSchematic worldRenderer, IChunkRendererFactory factory)
    {
        this.renderer = worldRenderer;
        this.world = world;
        this.setViewDistance(viewDistance);
        this.createChunks(factory);
    }

    protected void createChunks(IChunkRendererFactory factory)
    {
        int numRenderers = this.sizeX * this.sizeY * this.sizeZ;
        this.renderers = new ChunkRendererSchematicVbo[numRenderers];

        for (int x = 0; x < this.sizeX; ++x)
        {
            for (int y = 0; y < this.sizeY; ++y)
            {
                for (int z = 0; z < this.sizeZ; ++z)
                {
                    int index = this.getChunkIndex(x, y, z);

                    this.renderers[index] = factory.create(this.world, this.renderer);
                    this.renderers[index].setPosition(x * 16, y * 16, z * 16);
                }
            }
        }
    }

    public void delete()
    {
	    for (ChunkRendererSchematicVbo chunkRendererSchematicVbo : this.renderers) {
		    chunkRendererSchematicVbo.deleteGlResources();
	    }
    }

    private int getChunkIndex(int x, int y, int z)
    {
        return (z * this.sizeY + y) * this.sizeX + x;
    }

    protected void setViewDistance(int viewDistance)
    {
        int diameter = viewDistance * 2 + 1;
        this.sizeX = diameter;
        this.sizeY = this.world.getHeight() >> 4;
        this.sizeZ = diameter;
    }

    public void updateCameraPosition(double cameraX, double cameraZ)
    {
        int int_1 = Mth.floor(cameraX) - 8;
        int int_2 = Mth.floor(cameraZ) - 8;
        int diameterX = this.sizeX * 16;

        for (int x = 0; x < this.sizeX; ++x)
        {
            int blockX = this.getCoordinate(int_1, diameterX, x);

            for (int z = 0; z < this.sizeZ; ++z)
            {
                int blockZ = this.getCoordinate(int_2, diameterX, z);

                for (int y = 0; y < this.sizeY; ++y)
                {
                    int blockY = this.world.getMinBuildHeight() + y * 16;
                    this.renderers[this.getChunkIndex(x, y, z)].setPosition(blockX, blockY, blockZ);
                }
            }
        }
    }

    private int getCoordinate(int int_1, int diameter, int relChunkPos)
    {
        int int_4 = relChunkPos * 16;
        int int_5 = int_4 - int_1 + diameter / 2;

        if (int_5 < 0)
        {
            int_5 -= diameter - 1;
        }

        return int_4 - int_5 / diameter * diameter;
    }

    public void scheduleChunkRender(int chunkX, int chunkY, int chunkZ, boolean immediate)
    {
        int bottomSectionY = this.world.getMinBuildHeight() >> 4;

        if (chunkY >= bottomSectionY && chunkY < bottomSectionY + this.sizeY)
        {
            chunkX = Math.floorMod(chunkX, this.sizeX);
            chunkY = Math.floorMod(chunkY - bottomSectionY, this.sizeY);
            chunkZ = Math.floorMod(chunkZ, this.sizeZ);

            this.renderers[this.getChunkIndex(chunkX, chunkY, chunkZ)].setNeedsUpdate(immediate);
        }
    }

    @Nullable
    protected ChunkRendererSchematicVbo getChunkRenderer(BlockPos pos)
    {
        int cx = Mth.intFloorDiv(pos.getX(), 16);
        int cy = Mth.intFloorDiv(pos.getY() - this.world.getMinBuildHeight(), 16);
        int cz = Mth.intFloorDiv(pos.getZ(), 16);

        if (cy >= 0 && cy < this.sizeY)
        {
            cx = Mth.positiveModulo(cx, this.sizeX);
            cz = Mth.positiveModulo(cz, this.sizeZ);

            return this.renderers[this.getChunkIndex(cx, cy, cz)];
        }

        return null;
    }
}
