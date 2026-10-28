package my.celium.org.brewingplus.client;

import java.lang.reflect.Field;

import my.celium.org.brewingplus.BrewingPlusMod;
import my.celium.org.client.MycelScreens;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.BrewingStandScreen;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;

/**
 * The small potion button in the brewing stand's top-right corner that
 * opens the {@link GuideScreen}. Each loader adds it after the stand screen
 * initialises. Position comes from the screen's {@code leftPos}/
 * {@code topPos}/{@code imageWidth}, read reflectively. Client only.
 */
public final class GuideButton extends AbstractButton {
    static final int SIZE = 18;
    static final int INSET = 4;

    private final BrewingStandScreen stand;
    private final ItemStack icon = PotionContents.createItemStack(Items.POTION, Potions.NIGHT_VISION);

    private GuideButton(BrewingStandScreen stand, int x, int y) {
        super(x, y, SIZE, SIZE, Component.literal("Brewing+"));
        this.stand = stand;
        setTooltip(Tooltip.create(Component.literal("Brewing+: how to brew every potion")));
    }

    /** The button for {@code screen}, or {@code null} when it is not a brewing stand or the button is off. */
    public static GuideButton create(Screen screen) {
        try {
            if (!(screen instanceof BrewingStandScreen stand) || !BrewingPlusMod.isModEnabled()
                    || !BrewingPlusMod.showButton.get()) {
                return null;
            }
            int left = readInt(stand, "leftPos");
            int top = readInt(stand, "topPos");
            int imageWidth = readInt(stand, "imageWidth");
            BrewingPlusMod.debug("Guide button added at ({}, {})", left + imageWidth - SIZE - INSET, top + INSET);
            return new GuideButton(stand, left + imageWidth - SIZE - INSET, top + INSET);
        } catch (RuntimeException | ReflectiveOperationException e) {
            BrewingPlusMod.debug("Guide button not added: {}", e.toString());
            return null;
        }
    }

    @Override
    public void onPress(InputWithModifiers input) {
        BrewingPlusMod.debug("Guide button pressed");
        MycelScreens.open(new GuideScreen(stand));
    }

    @Override
    protected void extractContents(GuiGraphicsExtractor gfx, int mouseX, int mouseY, float a) {
        extractDefaultSprite(gfx);
        gfx.item(icon, getX() + 1, getY() + 1);
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        defaultButtonNarrationText(output);
    }

    static int readInt(AbstractContainerScreen<?> screen, String name) throws ReflectiveOperationException {
        Field field = AbstractContainerScreen.class.getDeclaredField(name);
        field.setAccessible(true);
        return field.getInt(screen);
    }
}
