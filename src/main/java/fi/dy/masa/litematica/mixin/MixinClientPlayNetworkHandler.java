package fi.dy.masa.litematica.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import fi.dy.masa.litematica.Litematica;
import fi.dy.masa.litematica.config.Configs;
import fi.dy.masa.litematica.data.DataManager;
import fi.dy.masa.litematica.util.SchematicWorldRefresher;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundForgetLevelChunkPacket;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket;

@Mixin(ClientPacketListener.class)
public abstract class MixinClientPlayNetworkHandler {
    @Inject(method = "handleLevelChunkWithLight", at = @At("RETURN"))
    private void litematica_onUpdateChunk(ClientboundLevelChunkWithLightPacket packet, CallbackInfo ci) {
        int chunkX = packet.getX();
        int chunkZ = packet.getZ();
        Litematica.debugLog("MixinClientPlayNetworkHandler#litematica_onUpdateChunk({}, {})", chunkX, chunkZ);

        if (Configs.Visuals.ENABLE_RENDERING.getBooleanValue()
                && Configs.Visuals.ENABLE_SCHEMATIC_RENDERING.getBooleanValue()) {
            SchematicWorldRefresher.INSTANCE.markSchematicChunksForRenderUpdate(chunkX, chunkZ);
        }

        DataManager.getSchematicPlacementManager().onClientChunkLoad(chunkX, chunkZ);
        // TODO verifier updates?
    }

    @Inject(method = "handleForgetLevelChunk", at = @At("RETURN"))
    private void litematica_onChunkUnload(ClientboundForgetLevelChunkPacket packet, CallbackInfo ci) {
        if (!Configs.Generic.LOAD_ENTIRE_SCHEMATICS.getBooleanValue()) {
            Litematica.debugLog(
                    "MixinClientPlayNetworkHandler#litematica_onChunkUnload({}, {})", packet.getX(), packet.getZ());
            DataManager.getSchematicPlacementManager().onClientChunkUnload(packet.getX(), packet.getZ());
        }
    }

    @Inject(
            method = "handleSystemChat",
            cancellable = true,
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/client/multiplayer/chat/ChatListener;handleSystemMessage(Lnet/minecraft/network/chat/Component;Z)V"))
    private void litematica_onGameMessage(ClientboundSystemChatPacket packet, CallbackInfo ci) {
        if (DataManager.onChatMessage(packet.content())) {
            ci.cancel();
        }
    }
}
