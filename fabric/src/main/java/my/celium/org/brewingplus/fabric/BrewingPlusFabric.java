package my.celium.org.brewingplus.fabric;

import my.celium.org.brewingplus.MissingMycel;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;

/**
 * Fabric entrypoint. Without Mycel the game still starts: Brewing+ stays off and tells every player who
 * joins. Everything that needs Mycel lives in {@link FabricSetup}.
 */
public final class BrewingPlusFabric implements ModInitializer {
    @Override
    public void onInitialize() {
        if (!FabricLoader.getInstance().isModLoaded("mycel")) {
            MissingMycel.logOnce();
            ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> MissingMycel.tell(handler.getPlayer()));
            return;
        }
        FabricSetup.start();
    }
}
