package my.celium.org.brewingplus.fabric;

import my.celium.org.brewingplus.client.GuideButton;
import my.celium.org.brewingplus.client.StandHint;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.gui.screens.inventory.BrewingStandScreen;

public final class BrewingPlusFabricClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
            if (!(screen instanceof BrewingStandScreen)) {
                return;
            }
            GuideButton button = GuideButton.create(screen);
            if (button != null) {
                Screens.getWidgets(screen).add(button);
            }
            ScreenHooks.afterForeground(screen,
                    (foregroundScreen, gfx, mouseX, mouseY) -> StandHint.render(gfx, foregroundScreen, mouseX, mouseY));
        });
    }
}
