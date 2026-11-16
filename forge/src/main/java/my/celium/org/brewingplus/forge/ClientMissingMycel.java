package my.celium.org.brewingplus.forge;

import my.celium.org.brewingplus.MissingMycel;
import net.minecraft.client.Minecraft;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;

/** Without Mycel: one chat line when joining a server (singleplayer worlds get it from the server side). */
final class ClientMissingMycel {
    private ClientMissingMycel() {
    }

    static void register() {
        ClientPlayerNetworkEvent.LoggingIn.BUS.addListener(event -> {
            if (!Minecraft.getInstance().isLocalServer() && event.getPlayer() != null) {
                event.getPlayer().sendSystemMessage(MissingMycel.message());
            }
        });
    }
}
