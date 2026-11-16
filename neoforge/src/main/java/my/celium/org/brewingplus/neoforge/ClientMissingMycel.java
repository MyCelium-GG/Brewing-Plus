package my.celium.org.brewingplus.neoforge;

import my.celium.org.brewingplus.MissingMycel;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.common.NeoForge;

/** Without Mycel: one chat line when joining a server (singleplayer worlds get it from the server side). */
final class ClientMissingMycel {
    private ClientMissingMycel() {
    }

    static void register() {
        NeoForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingIn event) -> {
            if (!Minecraft.getInstance().isLocalServer() && event.getPlayer() != null) {
                event.getPlayer().sendSystemMessage(MissingMycel.message());
            }
        });
    }
}
