package my.celium.org.brewingplus.fabric;

import my.celium.org.brewingplus.BrewingPlusMod;
import net.fabricmc.api.ModInitializer;

/**
 * Fabric entrypoint: server-safe bootstrap only. Client screen hooks live
 * in {@link BrewingPlusFabricClient} so dedicated servers never load
 * client classes.
 */
public final class BrewingPlusFabric implements ModInitializer {
    @Override
    public void onInitialize() {
        BrewingPlusMod.init();
    }
}
