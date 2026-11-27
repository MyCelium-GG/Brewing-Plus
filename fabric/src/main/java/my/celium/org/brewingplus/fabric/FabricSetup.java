package my.celium.org.brewingplus.fabric;

import my.celium.org.brewingplus.BrewingPlusMod;
import my.celium.org.brewingplus.stand.StandServer;
import my.celium.org.fabric.FabricNetworking;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

/** Only loaded when Mycel is installed: config, the stand packets and their server tick. */
final class FabricSetup {
    private FabricSetup() {
    }

    static void start() {
        BrewingPlusMod.init();
        // Packets registered after Mycel's own entrypoint need this on Fabric, or they are never
        // registered and the stand card and the ready message get nothing from the server.
        FabricNetworking.flush();
        ServerTickEvents.END_SERVER_TICK.register(StandServer::tick);
    }
}
