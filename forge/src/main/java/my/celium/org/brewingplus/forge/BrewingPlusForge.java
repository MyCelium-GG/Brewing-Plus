package my.celium.org.brewingplus.forge;

import my.celium.org.client.ClientHooks;
import my.celium.org.brewingplus.BrewingPlusMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.ConfigScreenHandler;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;

/**
 * Forge entrypoint: server-safe bootstrap; client hooks (config screen,
 * stand button, stand hint) are isolated in {@link ClientBrewingPlus} so
 * dedicated servers never load client classes.
 */
@Mod(BrewingPlusMod.ID)
public final class BrewingPlusForge {
    public BrewingPlusForge(@SuppressWarnings("unused") FMLJavaModLoadingContext context) {
        BrewingPlusMod.init();
        if (FMLEnvironment.dist == Dist.CLIENT) {
            ModLoadingContext.get().registerExtensionPoint(ConfigScreenHandler.ConfigScreenFactory.class,
                    () -> new ConfigScreenHandler.ConfigScreenFactory(
                            (Minecraft minecraft, Screen parent) -> ClientHooks.createConfigScreen(parent,
                                    BrewingPlusMod.ID)));
            ClientBrewingPlus.register();
        }
    }
}
