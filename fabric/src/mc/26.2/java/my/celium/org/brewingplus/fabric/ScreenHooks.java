package my.celium.org.brewingplus.fabric;

import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;

/** Fabric screen events that differ between Minecraft lines; the 26.2 copy (one per line in fabric/src/mc). */
final class ScreenHooks {
    private ScreenHooks() {
    }

    interface Draw {
        void draw(Screen screen, GuiGraphicsExtractor gfx, int mouseX, int mouseY);
    }

    /** Runs {@code draw} after {@code screen} draws its contents, every frame. */
    static void afterForeground(Screen screen, Draw draw) {
        ScreenEvents.afterForeground(screen).register((current, gfx, mouseX, mouseY, tickDelta) -> draw.draw(current, gfx, mouseX, mouseY));
    }
}
