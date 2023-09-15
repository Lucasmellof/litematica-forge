package fi.dy.masa.litematica.mixin;

import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(SignBlockEntity.class)
public interface IMixinSignBlockEntity {
    @Accessor("messages")
    Component[] litematica_getText();
}
