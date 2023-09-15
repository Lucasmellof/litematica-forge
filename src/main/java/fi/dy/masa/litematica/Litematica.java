package fi.dy.masa.litematica;

import fi.dy.masa.litematica.gui.GuiConfigs;
import fi.dy.masa.malilib.MaLiLibConfigGui;
import net.minecraftforge.client.ConfigScreenHandler;
import net.minecraftforge.fml.IExtensionPoint;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.network.NetworkConstants;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import fi.dy.masa.malilib.event.InitializationHandler;
import fi.dy.masa.litematica.config.Configs;

@Mod(Reference.MOD_ID)
public class Litematica {
	public static final Logger logger = LogManager.getLogger(Reference.MOD_ID);

	public Litematica() {
		ModLoadingContext.get().registerExtensionPoint(IExtensionPoint.DisplayTest.class, ()->new IExtensionPoint.DisplayTest(()->"ANY", (remote, isServer)-> true));

		FMLJavaModLoadingContext.get().getModEventBus().addListener(this::onClientSetup);
		InitializationHandler.getInstance().registerInitializationHandler(new InitHandler());
	}
	private void onClientSetup(final FMLClientSetupEvent event)
	{
		// Make sure the mod being absent on the other network side does not cause
		// the client to display the server as incompatible
		ModLoadingContext ctx = ModLoadingContext.get();
		ctx.registerExtensionPoint(
				IExtensionPoint.DisplayTest.class,
				() -> new IExtensionPoint.DisplayTest(
						() -> NetworkConstants.IGNORESERVERONLY, (remote, isServer) -> true));
		ctx.registerExtensionPoint(
				ConfigScreenHandler.ConfigScreenFactory.class,
				() -> new ConfigScreenHandler.ConfigScreenFactory((minecraft, screen) -> {
					var gui = new GuiConfigs();
					gui.setParent(screen);
					return gui;
				}));
		//        MinecraftForge.EVENT_BUS.register(new ForgeInputEventHandler());
		//        MinecraftForge.EVENT_BUS.register(new ForgeRenderEventHandler());
		//        MinecraftForge.EVENT_BUS.register(new ForgeTickEventHandler());
		//        MinecraftForge.EVENT_BUS.register(new ForgeWorldEventHandler());

		InitializationHandler.getInstance().registerInitializationHandler(new InitHandler());
	}

	public static void debugLog(String msg, Object... args) {
		if (Configs.Generic.DEBUG_LOGGING.getBooleanValue()) {
			Litematica.logger.info(msg, args);
		}
	}
}
