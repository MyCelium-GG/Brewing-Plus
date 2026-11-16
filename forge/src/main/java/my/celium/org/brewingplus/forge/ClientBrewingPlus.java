package my.celium.org.brewingplus.forge;

import my.celium.org.brewingplus.BrewingPlusMod;
import my.celium.org.brewingplus.client.StandCard;
import my.celium.org.brewingplus.client.StandClient;
import net.minecraft.resources.Identifier;
import net.minecraftforge.client.event.AddGuiOverlayLayersEvent;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import my.celium.org.brewingplus.client.GuideButton;
import my.celium.org.brewingplus.client.StandHint;
import net.minecraftforge.client.event.ContainerScreenEvent;
import net.minecraftforge.client.event.ScreenEvent;

public final class ClientBrewingPlus {
    private ClientBrewingPlus() {
    }

    public static void register() {
        ScreenEvent.Init.Post.BUS.addListener(ClientBrewingPlus::onScreenInit);
        ContainerScreenEvent.Render.Foreground.BUS.addListener(ClientBrewingPlus::onContainerForeground);
        AddGuiOverlayLayersEvent.BUS.addListener(event -> event.getLayeredDraw().add(
                Identifier.fromNamespaceAndPath(BrewingPlusMod.ID, "stand_card"), (gfx, tracker) -> StandCard.render(gfx)));
        ClientPlayerNetworkEvent.LoggingOut.BUS.addListener(event -> StandClient.clear());
    }

    private static void onScreenInit(ScreenEvent.Init.Post event) {
        GuideButton button = GuideButton.create(event.getScreen());
        if (button != null) {
            event.addListener(button);
        }
    }

    private static void onContainerForeground(ContainerScreenEvent.Render.Foreground event) {
        StandHint.render(event.getGuiGraphics(), event.getContainerScreen(), event.getMouseX(), event.getMouseY());
    }
}
