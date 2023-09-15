package fi.dy.masa.litematica.network;

import java.util.List;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import com.google.common.collect.ImmutableList;
import fi.dy.masa.litematica.data.DataManager;
import fi.dy.masa.malilib.network.IPluginChannelHandler;

public class CarpetHelloPacketHandler implements IPluginChannelHandler {
    public static final CarpetHelloPacketHandler INSTANCE = new CarpetHelloPacketHandler();

    private final List<ResourceLocation> channels = ImmutableList.of(new ResourceLocation("carpet:hello"));

    @Override
    public boolean registerToServer() {
        return false;
    }

    @Override
    public boolean usePacketSplitter() {
        return false;
    }

    @Override
    public List<ResourceLocation> getChannels() {
        return this.channels;
    }

    @Override
    public void onPacketReceived(FriendlyByteBuf buf) {
        DataManager.setIsCarpetServer(true);
    }
}
