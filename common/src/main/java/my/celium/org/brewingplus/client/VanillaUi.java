package my.celium.org.brewingplus.client;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

/**
 * Drawing in the look of vanilla's own containers: the grey bevelled panel, recessed boxes, slots,
 * the creative-inventory scroller and the dark item-tooltip box. Text colours are the ones that read
 * on the grey panel (vanilla's container label grey and darker status colours). Client only.
 */
final class VanillaUi {
    static final int BODY = 0xFFC6C6C6;
    static final int LIGHT = 0xFFFFFFFF;
    static final int SHADOW = 0xFF555555;
    static final int EDGE = 0xFF000000;
    static final int SLOT_DARK = 0xFF373737;
    static final int SLOT_FILL = 0xFF8B8B8B;

    /** Container labels ("Inventory", "Brewing Stand"). */
    static final int LABEL = 0xFF404040;
    static final int DIM = 0xFF6B6B6B;
    static final int GOOD = 0xFF2E6B1F;
    static final int BAD = 0xFFAA2020;
    static final int WARN = 0xFF8A5A00;
    /** Hover and selection tint over grey, like vanilla's slot highlight. */
    static final int HOVER = 0x80FFFFFF;

    /** Tooltip colours, for text on the dark box. */
    static final int TIP_TEXT = 0xFFFFFFFF;
    static final int TIP_DIM = 0xFFAAAAAA;
    static final int TIP_TITLE = 0xFFFFFF55;
    static final int TIP_GOOD = 0xFF55FF55;
    static final int TIP_BAD = 0xFFFF5555;
    static final int TIP_WARN = 0xFFFFAA00;

    static final int SCROLLER_W = 12;
    static final int SCROLLER_H = 15;
    private static final Identifier SLOT = Identifier.withDefaultNamespace("container/slot");
    private static final Identifier SCROLLER = Identifier.withDefaultNamespace("container/creative_inventory/scroller");
    private static final Identifier SCROLLER_OFF =
            Identifier.withDefaultNamespace("container/creative_inventory/scroller_disabled");

    private VanillaUi() {
    }

    /** The grey container panel with the bevelled edge that every vanilla inventory has. */
    static void panel(GuiGraphicsExtractor gfx, int x, int y, int w, int h) {
        gfx.fill(x + 1, y, x + w - 1, y + 1, EDGE);
        gfx.fill(x + 1, y + h - 1, x + w - 1, y + h, EDGE);
        gfx.fill(x, y + 1, x + 1, y + h - 1, EDGE);
        gfx.fill(x + w - 1, y + 1, x + w, y + h - 1, EDGE);
        gfx.fill(x + 1, y + 1, x + w - 1, y + h - 1, BODY);
        gfx.fill(x + 1, y + 1, x + w - 3, y + 3, LIGHT);
        gfx.fill(x + 1, y + 1, x + 3, y + h - 3, LIGHT);
        gfx.fill(x + 3, y + h - 3, x + w - 1, y + h - 1, SHADOW);
        gfx.fill(x + w - 3, y + 3, x + w - 1, y + h - 1, SHADOW);
    }

    /** A recessed area (dark top-left edge, light bottom-right), filled with {@code fill}. */
    static void inset(GuiGraphicsExtractor gfx, int x, int y, int w, int h, int fill) {
        gfx.fill(x, y, x + w, y + h, fill);
        gfx.fill(x, y, x + w - 1, y + 1, SLOT_DARK);
        gfx.fill(x, y, x + 1, y + h - 1, SLOT_DARK);
        gfx.fill(x + 1, y + h - 1, x + w, y + h, LIGHT);
        gfx.fill(x + w - 1, y + 1, x + w, y + h, LIGHT);
    }

    /** An empty inventory slot, 18 x 18, from vanilla's own sprite. */
    static void slot(GuiGraphicsExtractor gfx, int x, int y) {
        gfx.blitSprite(RenderPipelines.GUI_TEXTURED, SLOT, x, y, 18, 18);
    }

    /** The creative inventory's scrollbar: a recessed 14 px track with the scroller in it. */
    static void scrollbar(GuiGraphicsExtractor gfx, int x, int top, int height, int scroll, int maxScroll) {
        inset(gfx, x, top, SCROLLER_W + 2, height, SLOT_FILL);
        int travel = height - 2 - SCROLLER_H;
        int y = top + 1 + (maxScroll <= 0 ? 0 : travel * scroll / maxScroll);
        gfx.blitSprite(RenderPipelines.GUI_TEXTURED, maxScroll > 0 ? SCROLLER : SCROLLER_OFF, x + 1, y,
                SCROLLER_W, SCROLLER_H);
    }

    /** Vanilla's item tooltip box: near-black with the purple gradient frame. */
    static void tooltip(GuiGraphicsExtractor gfx, int x0, int y0, int x1, int y1) {
        int bg = 0xF0100010;
        gfx.fill(x0, y0 - 1, x1, y0, bg);
        gfx.fill(x0, y1, x1, y1 + 1, bg);
        gfx.fill(x0 - 1, y0, x0, y1, bg);
        gfx.fill(x1, y0, x1 + 1, y1, bg);
        gfx.fill(x0, y0, x1, y1, bg);
        int top = 0x505000FF;
        int bottom = 0x5028007F;
        gfx.fill(x0, y0, x1, y0 + 1, top);
        gfx.fill(x0, y1 - 1, x1, y1, bottom);
        gfx.fillGradient(x0, y0 + 1, x0 + 1, y1 - 1, top, bottom);
        gfx.fillGradient(x1 - 1, y0 + 1, x1, y1 - 1, top, bottom);
    }

    /** Label-style text: no shadow, cut with "..." when wider than {@code room}. */
    static void label(GuiGraphicsExtractor gfx, Font font, String text, int x, int y, int color, int room) {
        gfx.text(font, fit(font, text, room), x, y, color, false);
    }

    static String fit(Font font, String text, int room) {
        if (font.width(text) <= room) {
            return text;
        }
        return font.plainSubstrByWidth(text, Math.max(0, room - font.width("..."))) + "...";
    }
}
