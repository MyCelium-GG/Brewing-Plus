package my.celium.org.brewingplus.fabric;

import my.celium.org.brewingplus.client.GuideButton;
import my.celium.org.brewingplus.client.StandHint;
import my.celium.org.brewingplus.BrewingPlusMod;
import my.celium.org.brewingplus.client.StandCard;
import my.celium.org.brewingplus.client.StandClient;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.resources.Identifier;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.gui.screens.inventory.BrewingStandScreen;

/** Fabric client setup, only with Mycel installed. */
final class FabricClientSetup {
    private FabricClientSetup() {
    }

    static void start() {
        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath(BrewingPlusMod.ID, "stand_card"),
                (gfx, tracker) -> StandCard.render(gfx));
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> StandClient.clear());
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
