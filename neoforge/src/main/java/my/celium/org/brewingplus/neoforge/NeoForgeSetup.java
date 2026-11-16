package my.celium.org.brewingplus.neoforge;

import my.celium.org.brewingplus.BrewingPlusMod;
import my.celium.org.brewingplus.stand.StandServer;
import my.celium.org.client.ClientHooks;
import net.minecraft.client.gui.screens.Screen;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModLoadingContext;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Only loaded when Mycel is installed: config, the stand server tick and the client hooks. */
final class NeoForgeSetup {
    private NeoForgeSetup() {
    }

    static void start() {
        BrewingPlusMod.init();
        NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post event) -> StandServer.tick(event.getServer()));
        if (FMLEnvironment.getDist().isClient()) {
            ModLoadingContext.get().registerExtensionPoint(IConfigScreenFactory.class,
                    () -> (ModContainer ignored, Screen parent) -> ClientHooks.createConfigScreen(parent,
                            BrewingPlusMod.ID));
            ClientBrewingPlus.register();
        }
    }
}
