package my.celium.org.brewingplus.forge;

import my.celium.org.brewingplus.BrewingPlusMod;
import my.celium.org.brewingplus.stand.StandServer;
import my.celium.org.client.ClientHooks;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.ConfigScreenHandler;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;

/** Only loaded when Mycel is installed: config, the stand server tick and the client hooks. */
final class ForgeSetup {
    private ForgeSetup() {
    }

    static void start() {
        BrewingPlusMod.init();
        TickEvent.ServerTickEvent.Post.BUS.addListener(event -> StandServer.tick(event.server()));
        if (FMLEnvironment.dist == Dist.CLIENT) {
            ModLoadingContext.get().registerExtensionPoint(ConfigScreenHandler.ConfigScreenFactory.class,
                    () -> new ConfigScreenHandler.ConfigScreenFactory(
                            (Minecraft minecraft, Screen parent) -> ClientHooks.createConfigScreen(parent,
                                    BrewingPlusMod.ID)));
            ClientBrewingPlus.register();
        }
    }
}
