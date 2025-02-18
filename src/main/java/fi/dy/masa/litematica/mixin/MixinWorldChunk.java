package fi.dy.masa.litematica.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.Slice;
import fi.dy.masa.litematica.util.WorldUtils;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;

@Mixin(LevelChunk.class)
public abstract class MixinWorldChunk {
    @Redirect(
            method = "setBlockState",
            slice =
                    @Slice(
                            from =
                                    @At(
                                            value = "INVOKE",
                                            target =
                                                    "Lnet/minecraft/world/level/chunk/LevelChunkSection;getBlockState(III)Lnet/minecraft/world/level/block/state/BlockState;")),
            at = @At(value = "FIELD", target = "Lnet/minecraft/world/level/Level;isClientSide:Z", ordinal = 0))
    private boolean litematica_redirectIsRemote(Level world) {
        return WorldUtils.shouldPreventBlockUpdates(world) || world.isClientSide;
    }
}
