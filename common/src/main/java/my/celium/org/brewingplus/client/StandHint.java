package my.celium.org.brewingplus.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.joml.Matrix3x2fStack;

import my.celium.org.brewingplus.BrewGraph;
import my.celium.org.brewingplus.BrewGraph.Edge;
import my.celium.org.brewingplus.BrewGraph.Route;
import my.celium.org.brewingplus.BrewingPlusMod;
import my.celium.org.client.MycelTheme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.BrewingStandScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.inventory.BrewingStandMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * While a potion is tracked, the brewing stand shows what to do next: a
 * ghost of the next ingredient in the empty ingredient slot (or of the
 * starting bottle in empty bottle slots) and a small card beside the stand,
 * e.g. "Next: Golden Carrot · 2 steps left". Every loader calls
 * {@link #render} from its container foreground hook. Never throws.
 */
public final class StandHint {
    /** Fades a fake item into a ghost, like vanilla's recipe book ghost slots. */
    private static final int GHOST_FADE = 0x998B8B8B;

    static final int INGREDIENT_SLOT = 3;
    static final int FUEL_SLOT = 4;
    static final int CARD_GAP = 4;
    static final int CARD_MAX_W = 150;
    static final int CARD_MIN_W = 70;
    static final int LINE_H = 11;

    private StandHint() {
    }

    public static void render(GuiGraphicsExtractor gfx, Screen screen, int mouseX, int mouseY) {
        if (gfx == null || !(screen instanceof BrewingStandScreen stand)) {
            return;
        }
        try {
            if (!BrewingPlusMod.isModEnabled() || !BrewingPlusMod.showStandHint.get()
                    || GuideState.tracked() == null) {
                return;
            }
            renderInner(gfx, stand);
        } catch (RuntimeException | ReflectiveOperationException e) {
            // Screens must never crash the frame.
        }
    }

    private record Line(String text, int color) {
    }

    private static void renderInner(GuiGraphicsExtractor gfx, BrewingStandScreen stand)
            throws ReflectiveOperationException {
        BrewingData data = BrewingData.get();
        if (data == null) {
            return;
        }
        String tracked = GuideState.tracked();
        Minecraft minecraft = Minecraft.getInstance();
        Font font = minecraft.font;
        BrewingStandMenu menu = stand.getMenu();
        int left = GuideButton.readInt(stand, "leftPos");
        int top = GuideButton.readInt(stand, "topPos");
        int imageWidth = GuideButton.readInt(stand, "imageWidth");
        // Some loaders fire the foreground hook with the pose already moved to the stand's corner.
        Matrix3x2fStack pose = gfx.pose();
        int baseX = left - Math.round(pose.m20());
        int baseY = top - Math.round(pose.m21());

        List<Line> lines = new ArrayList<>();
        lines.add(new Line(PotionText.full(data.bottle(tracked)), MycelTheme.HEADING));

        List<ItemStack> bottles = new ArrayList<>();
        for (int i = 0; i < INGREDIENT_SLOT; i++) {
            ItemStack stack = menu.getSlot(i).getItem();
            if (!stack.isEmpty()) {
                bottles.add(stack);
            }
        }
        Slot ingredientSlot = menu.getSlot(INGREDIENT_SLOT);
        Map<String, Integer> counts = GuideScreen.inventoryCounts(minecraft, menu);

        if (bottles.isEmpty()) {
            Route route = data.routeFromWater(tracked);
            if (route == null) {
                lines.add(new Line("No known recipe makes this", MycelTheme.BAD));
            } else {
                ItemStack start = data.bottle(route.start());
                for (int i = 0; i < INGREDIENT_SLOT; i++) {
                    ghost(gfx, start, baseX + menu.getSlot(i).x, baseY + menu.getSlot(i).y);
                }
                lines.add(new Line("Add 1-3 " + PotionText.name(start), MycelTheme.TEXT));
                lines.add(new Line(stepsLeft(route.steps().size()), MycelTheme.DIM));
            }
        } else {
            String from = BrewingData.keyOf(bottles.get(0));
            boolean mixed = false;
            for (ItemStack bottle : bottles) {
                mixed |= !BrewingData.keyOf(bottle).equals(from);
            }
            Route route = from.equals(tracked) ? null : data.graph().route(from, tracked);
            if (from.equals(tracked)) {
                lines.add(new Line("Done! Take your potions", MycelTheme.GOOD));
            } else if (route == null) {
                lines.add(new Line("These bottles can't become this", MycelTheme.BAD));
                lines.add(new Line("Start again from " + PotionText.name(startBottle(data, tracked)), MycelTheme.DIM));
            } else {
                Edge next = route.steps().get(0);
                String pick = BrewGraph.pickReagents(route, counts).get(0);
                String reagentId = pick != null ? pick : next.reagents().get(0);
                ItemStack reagent = data.reagent(reagentId);
                ItemStack inSlot = ingredientSlot.getItem();
                if (inSlot.isEmpty()) {
                    ghost(gfx, reagent, baseX + ingredientSlot.x, baseY + ingredientSlot.y);
                    lines.add(new Line("Next: " + PotionText.name(reagent), MycelTheme.TEXT));
                    lines.add(new Line(pick == null ? "You have none" : "You have " + counts.getOrDefault(pick, 0),
                            pick == null ? MycelTheme.BAD : MycelTheme.DIM));
                } else if (next.reagents().contains(BrewingData.idOf(inSlot.getItem()))) {
                    lines.add(new Line("Brewing " + PotionText.name(inSlot), MycelTheme.GOOD));
                } else {
                    lines.add(new Line("Wrong ingredient", MycelTheme.BAD));
                    lines.add(new Line("Use " + PotionText.name(reagent), MycelTheme.TEXT));
                }
                lines.add(new Line(stepsLeft(route.steps().size()), MycelTheme.DIM));
            }
            if (mixed) {
                lines.add(new Line("Mixed bottles: following the first", MycelTheme.WARN));
            }
        }
        if (menu.getFuel() <= 0 && menu.getSlot(FUEL_SLOT).getItem().isEmpty()) {
            lines.add(new Line("No fuel: add Blaze Powder", MycelTheme.WARN));
        }
        card(gfx, font, lines, baseX, baseY, imageWidth, stand.width - Math.round(pose.m20()));
    }

    private static ItemStack startBottle(BrewingData data, String tracked) {
        Route route = data.routeFromWater(tracked);
        return data.bottle(route == null ? BrewGraph.WATER : route.start());
    }

    private static String stepsLeft(int steps) {
        return steps == 1 ? "1 step left" : steps + " steps left";
    }

    /** Vanilla-style ghost item: the item, faded towards the slot color. */
    static void ghost(GuiGraphicsExtractor gfx, ItemStack stack, int x, int y) {
        if (stack.isEmpty()) {
            return;
        }
        gfx.fakeItem(stack, x, y);
        gfx.fill(x, y, x + 16, y + 16, GHOST_FADE);
    }

    /**
     * The card beside the stand, on whichever side has more room (right on
     * ties), with lines wrapped to fit. Skipped when neither side fits a word.
     */
    private static void card(GuiGraphicsExtractor gfx, Font font, List<Line> lines, int baseX, int baseY,
            int imageWidth, int screenRight) {
        int screenLeft = -Math.round(gfx.pose().m20());
        int rightRoom = screenRight - (baseX + imageWidth) - CARD_GAP - 4;
        int leftRoom = baseX - screenLeft - CARD_GAP - 4;
        int room = Math.min(CARD_MAX_W, Math.max(rightRoom, leftRoom));
        if (room < CARD_MIN_W) {
            return;
        }
        int textWidth = font.width("Brewing+");
        for (Line line : lines) {
            textWidth = Math.max(textWidth, font.width(line.text()));
        }
        int w = Math.min(room, textWidth + 12);
        List<List<FormattedCharSequence>> wrapped = new ArrayList<>();
        int lineCount = 0;
        for (Line line : lines) {
            List<FormattedCharSequence> parts = font.split(Component.literal(line.text()), w - 12);
            wrapped.add(parts);
            lineCount += parts.size();
        }
        int h = 18 + lineCount * LINE_H + 4;
        int x0 = rightRoom >= leftRoom ? baseX + imageWidth + CARD_GAP : baseX - CARD_GAP - w;
        int y0 = baseY;
        MycelTheme.panel(gfx, x0, y0, x0 + w, y0 + h);
        gfx.fill(x0 + 2, y0 + 2, x0 + w - 2, y0 + 14, MycelTheme.ACCENT_DARK);
        gfx.text(font, "Brewing+", x0 + 6, y0 + 4, MycelTheme.HEADING);
        int y = y0 + 18;
        for (int i = 0; i < lines.size(); i++) {
            for (FormattedCharSequence part : wrapped.get(i)) {
                gfx.text(font, part, x0 + 6, y, lines.get(i).color());
                y += LINE_H;
            }
        }
    }
}
