package fi.dy.masa.litematica.world;

import java.util.OptionalLong;
import javax.annotation.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.data.BuiltinRegistries;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.dimension.BuiltinDimensionTypes;
import net.minecraft.world.level.dimension.DimensionType;
import fi.dy.masa.litematica.Litematica;
import fi.dy.masa.litematica.render.LitematicaRenderer;

public class SchematicWorldHandler {
    private static final DimensionType END = BuiltinRegistries.DIMENSION_TYPE
            .getHolderOrThrow(BuiltinDimensionTypes.END)
            .value();
    private static final DimensionType DIMENSIONTYPE = new DimensionType(
            OptionalLong.of(6000L),
            false,
            false,
            false,
            false,
            1.0,
            false,
            false,
            -64,
            384,
            384,
            BlockTags.INFINIBURN_END,
            BuiltinDimensionTypes.OVERWORLD_EFFECTS,
            0.0F,
            END.monsterSettings());

    @Nullable
    private static WorldSchematic world;

    @Nullable
    public static WorldSchematic getSchematicWorld() {
        if (world == null) {
            world = createSchematicWorld();
        }

        return world;
    }

    @Nullable
    public static WorldSchematic createSchematicWorld() {
        if (Minecraft.getInstance().level == null) {
            return null;
        }

        ClientLevel.ClientLevelData levelInfo = new ClientLevel.ClientLevelData(Difficulty.PEACEFUL, false, true);
        return new WorldSchematic(
                levelInfo,
                BuiltinRegistries.DIMENSION_TYPE.getHolderOrThrow(BuiltinDimensionTypes.OVERWORLD),
                Minecraft.getInstance()::getProfiler);
    }

    public static void recreateSchematicWorld(boolean remove) {
        if (remove) {
            Litematica.debugLog("Removing the schematic world...");
            world = null;
        } else {
            Litematica.debugLog("(Re-)creating the schematic world...");
            // Note: The dimension used here must have no skylight, because the custom Chunks don't have those arrays
            world = createSchematicWorld();
            Litematica.debugLog("Schematic world (re-)created: {}", world);
        }

        LitematicaRenderer.getInstance().onSchematicWorldChanged(world);
    }
}
