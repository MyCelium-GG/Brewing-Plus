package my.celium.org.brewingplus.client;

import java.util.ArrayList;
import java.util.List;

import my.celium.org.brewingplus.BrewingPlusMod;
import my.celium.org.brewingplus.stand.StandInfo;
import my.celium.org.client.MycelScreens;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.BrewingStandBlock;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

/**
 * Look at a brewing stand: a small card with its bottles, the brew's time left, fuel, and the next step of
 * the tracked potion. Needs the server part (always there in singleplayer). Client only; never throws.
 */
public final class StandCard {
    static final int TOP_Y = 14;
    static final int PAD_X = 8;
    static final int PAD_Y = 5;
    static final int LINE_H = 12;
    static final int MAX_W = 380;

    private record Line(String text, int color) {
    }

    private StandCard() {
    }

    public static void render(GuiGraphicsExtractor gfx) {
        if (gfx == null) {
            return;
        }
        try {
            renderInner(gfx);
        } catch (RuntimeException e) {
            // The HUD must never break the frame.
        }
    }

    private static void renderInner(GuiGraphicsExtractor gfx) {
        if (!BrewingPlusMod.isModEnabled() || !BrewingPlusMod.standCard.get()) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null || MycelScreens.current() != null) {
            return;
        }
        if (!(minecraft.hitResult instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK) {
            return;
        }
        BlockPos pos = hit.getBlockPos();
        if (!(minecraft.level.getBlockState(pos).getBlock() instanceof BrewingStandBlock)) {
            return;
        }
        StandInfo stand = StandClient.get(pos);
        if (stand == null) {
            return;
        }
        BrewingData data = BrewingData.get();
        List<Line> lines = new ArrayList<>();
        lines.add(new Line(minecraft.level.getBlockState(pos).getBlock().getName().getString(), VanillaUi.TIP_TITLE));
        List<String> filled = stand.filled();
        if (filled.isEmpty()) {
            lines.add(new Line(Tr.s("card.no_bottles"), VanillaUi.TIP_DIM));
        } else {
            lines.add(new Line(StandClient.counts(data, filled), VanillaUi.TIP_TEXT));
        }
        if (stand.brewTime() > 0 && !stand.ingredient().isEmpty()) {
            lines.add(new Line(Tr.s("card.brewing", StandClient.itemName(stand.ingredient())) + "   "
                    + time(stand.brewTime()), VanillaUi.TIP_TEXT));
        } else if (!filled.isEmpty()) {
            lines.add(new Line(stand.ingredient().isEmpty() ? Tr.s("card.waiting")
                    : Tr.s("card.does_nothing", StandClient.itemName(stand.ingredient())), VanillaUi.TIP_DIM));
        }
        if (stand.fuelBrews() <= 0) {
            lines.add(new Line(Tr.s("no_fuel"), VanillaUi.TIP_WARN));
        } else {
            lines.add(new Line(Tr.s("card.fuel", Tr.plural(stand.fuelBrews(), "brews")), VanillaUi.TIP_DIM));
        }
        if (!filled.isEmpty()) {
            String next = StandClient.nextStep(data, filled.get(0), stand.brewTime() > 0 ? stand.ingredient() : "");
            if (next != null) {
                lines.add(new Line(next, VanillaUi.TIP_TEXT));
            }
        }

        Font font = minecraft.font;
        int max = Math.max(120, Math.min(MAX_W, gfx.guiWidth() - 2 * PAD_X - 16));
        int width = 0;
        for (Line line : lines) {
            width = Math.max(width, Math.min(max, font.width(line.text())));
        }
        int center = gfx.guiWidth() / 2;
        int x0 = center - width / 2 - PAD_X;
        int x1 = center + width / 2 + PAD_X;
        int y1 = TOP_Y + PAD_Y * 2 + lines.size() * LINE_H;
        VanillaUi.tooltip(gfx, x0, TOP_Y, x1, y1);
        int y = TOP_Y + PAD_Y + 1;
        for (Line line : lines) {
            gfx.centeredText(font, VanillaUi.fit(font, line.text(), max), center, y, line.color());
            y += LINE_H;
        }
    }

    /** Ticks as real time, "0:12". */
    static String time(int ticks) {
        int seconds = (ticks + 19) / 20;
        return seconds / 60 + ":" + (seconds % 60 < 10 ? "0" : "") + seconds % 60;
    }
}
