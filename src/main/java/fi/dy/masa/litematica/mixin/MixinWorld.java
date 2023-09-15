package fi.dy.masa.litematica.mixin;

import org.spongepowered.asm.mixin.Mixin;
import fi.dy.masa.litematica.util.IWorldUpdateSuppressor;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Unique;

@Mixin(Level.class)
public class MixinWorld implements IWorldUpdateSuppressor {
    @Unique
    private boolean litematica_preventBlockUpdates;

    @Override
    public boolean litematica_getShouldPreventBlockUpdates() {
        return this.litematica_preventBlockUpdates;
    }

    @Override
    public void litematica_setShouldPreventBlockUpdates(boolean preventUpdates) {
        this.litematica_preventBlockUpdates = preventUpdates;
    }
}
