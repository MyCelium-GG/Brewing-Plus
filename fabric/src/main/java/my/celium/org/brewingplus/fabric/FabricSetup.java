package my.celium.org.brewingplus.fabric;

import my.celium.org.brewingplus.BrewingPlusMod;
import my.celium.org.brewingplus.stand.StandServer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

/** Only loaded when Mycel is installed: config, the stand packets and their server tick. */
final class FabricSetup {
    private FabricSetup() {
    }

    static void start() {
        BrewingPlusMod.init();
        ServerTickEvents.END_SERVER_TICK.register(StandServer::tick);
    }
}
