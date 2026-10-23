package my.celium.org.brewingplus.neoforge;

import my.celium.org.brewingplus.client.GuideButton;
import my.celium.org.brewingplus.client.StandHint;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.common.NeoForge;

public final class ClientBrewingPlus {
    private ClientBrewingPlus() {
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener(ClientBrewingPlus::onScreenInit);
        NeoForge.EVENT_BUS.addListener(ClientBrewingPlus::onScreenForeground);
    }

    private static void onScreenInit(ScreenEvent.Init.Post event) {
        GuideButton button = GuideButton.create(event.getScreen());
        if (button != null) {
            event.addListener(button);
        }
    }

    private static void onScreenForeground(ScreenEvent.Render.Foreground event) {
        StandHint.render(event.getGuiGraphics(), event.getScreen(), event.getMouseX(), event.getMouseY());
    }
}
