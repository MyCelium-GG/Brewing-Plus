package my.celium.org.brewingplus.neoforge;

import my.celium.org.client.ClientHooks;
import my.celium.org.brewingplus.BrewingPlusMod;
import net.minecraft.client.gui.screens.Screen;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModLoadingContext;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

/**
 * NeoForge entrypoint: server-safe bootstrap; client hooks (config screen,
 * stand button, stand hint) are isolated in {@link ClientBrewingPlus} so
 * dedicated servers never load client classes.
 */
@Mod(BrewingPlusMod.ID)
public final class BrewingPlusNeoForge {
    public BrewingPlusNeoForge(@SuppressWarnings("unused") IEventBus modBus,
            @SuppressWarnings("unused") ModContainer container) {
        BrewingPlusMod.init();
        if (FMLEnvironment.getDist().isClient()) {
            ModLoadingContext.get().registerExtensionPoint(IConfigScreenFactory.class,
                    () -> (ModContainer ignored, Screen parent) -> ClientHooks.createConfigScreen(parent,
                            BrewingPlusMod.ID));
            ClientBrewingPlus.register();
        }
    }
}
