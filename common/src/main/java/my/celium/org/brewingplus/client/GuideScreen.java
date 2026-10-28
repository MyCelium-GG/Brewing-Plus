package my.celium.org.brewingplus.client;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import com.mojang.blaze3d.platform.InputConstants;

import my.celium.org.brewingplus.BrewGraph;
import my.celium.org.brewingplus.BrewGraph.Edge;
import my.celium.org.brewingplus.BrewGraph.Route;
import my.celium.org.brewingplus.BrewingPlusMod;
import my.celium.org.brewingplus.client.GuideState.Form;
import my.celium.org.brewingplus.client.GuideState.Tab;
import my.celium.org.client.MycelScreens;
import my.celium.org.client.MycelTheme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.BrewingStandScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.inventory.BrewingStandMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.alchemy.Potion;

/**
 * The guide opened from the brewing stand button. Two pages:
 * <ul>
 * <li><b>Potions</b>: a searchable grid of every potion the world's recipes
 * make (filter by bottle type or "have all"); the selected potion shows its
 * full chain from a water bottle with each ingredient and how many you carry,
 * what it brews into next, and other ways to make it.</li>
 * <li><b>Ingredients</b>: every reagent and every recipe it is used in.</li>
 * </ul>
 * "Track at stand" makes the stand show the next ingredient
 * ({@link StandHint}). Opening it over the stand keeps the stand open, so
 * closing it returns there with the bottles in place. Client only.
 */
public final class GuideScreen extends Screen {
    static final int TITLE_BAR = 18;
    static final int MARGIN = 8;
    static final int CELL = 18;
    static final int ROW_H = 22;
    static final int HEADER_H = 14;
    static final int FOOTER = 32;
    static final int TAB_Y = 24;
    static final int TAB_H = 14;
    static final int SEARCH_Y = 42;
    static final int CHIP_Y = 60;
    static final int CHIP_H = 12;
    static final int DETAIL_TOP = 50;
    static final int SUMMARY_LINES = 2;
    static final int LINE_H = 11;

    private final BrewingStandScreen parent;
    private final GuideState state = GuideState.CURRENT;
    private final BrewingData data;
    private final Map<String, Integer> counts;
    private final List<String> allPotions;
    private final List<String> allReagents;
    private List<String> filtered = List.of();
    private List<Row> rows = List.of();
    private int gridScroll;
    private int rowScroll;
    private EditBox search;
    private Button trackButton;

    public GuideScreen(BrewingStandScreen parent) {
        super(Component.literal("Brewing+"));
        this.parent = parent;
        Minecraft minecraft = Minecraft.getInstance();
        BrewingData.invalidate();
        this.data = BrewingData.get();
        this.counts = inventoryCounts(minecraft, parent == null ? null : parent.getMenu());
        this.allPotions = sortedPotions(data);
        this.allReagents = new ArrayList<>(data.graph().reagents());
        for (ItemStack unknown : data.unknownReagents()) {
            allReagents.add(BrewingData.idOf(unknown.getItem()));
        }
        if (state.selected != null && !allPotions.contains(state.selected)) {
            state.selected = null;
        }
        if (state.selectedReagent != null && !allReagents.contains(state.selectedReagent)) {
            state.selectedReagent = null;
        }
        preselectFromStand();
        filter();
        buildRows();
        BrewingPlusMod.debug("Guide opened: {} potions, {} reagents, source {}, selected {}",
                allPotions.size(), allReagents.size(), data.source(), state.selected);
    }

    /** Item id -> count over the player's inventory plus the stand's ingredient slot. */
    static Map<String, Integer> inventoryCounts(Minecraft minecraft, BrewingStandMenu menu) {
        Map<String, Integer> counts = new HashMap<>();
        LocalPlayer player = minecraft.player;
        if (player != null) {
            for (ItemStack stack : player.getInventory().getNonEquipmentItems()) {
                if (!stack.isEmpty()) {
                    counts.merge(BrewingData.idOf(stack.getItem()), stack.getCount(), Integer::sum);
                }
            }
        }
        if (menu != null) {
            ItemStack ingredient = menu.getSlot(StandHint.INGREDIENT_SLOT).getItem();
            if (!ingredient.isEmpty()) {
                counts.merge(BrewingData.idOf(ingredient.getItem()), ingredient.getCount(), Integer::sum);
            }
        }
        return counts;
    }

    /** Bottles held in the inventory or the stand that match {@code key}. */
    private int bottleCount(String key) {
        int count = 0;
        LocalPlayer player = minecraft == null ? null : minecraft.player;
        if (player != null) {
            for (ItemStack stack : player.getInventory().getNonEquipmentItems()) {
                if (!stack.isEmpty() && BrewingData.keyOf(stack).equals(key)) {
                    count += stack.getCount();
                }
            }
        }
        if (parent != null) {
            for (int i = 0; i < StandHint.INGREDIENT_SLOT; i++) {
                ItemStack stack = parent.getMenu().getSlot(i).getItem();
                if (!stack.isEmpty() && BrewingData.keyOf(stack).equals(key)) {
                    count += stack.getCount();
                }
            }
        }
        return count;
    }

    /** Drinkable, splash, lingering, others; then potion registry order (water, awkward, night vision...). */
    static List<String> sortedPotions(BrewingData data) {
        List<String> out = new ArrayList<>(data.brewable());
        out.sort(Comparator.comparingInt(GuideScreen::formOrder)
                .thenComparingInt(GuideScreen::potionOrder)
                .thenComparing(key -> key));
        return out;
    }

    private static int formOrder(String key) {
        String item = BrewGraph.itemOf(key);
        Form[] forms = Form.values();
        for (int i = 1; i < forms.length; i++) {
            if (forms[i].item.equals(item)) {
                return i;
            }
        }
        return forms.length;
    }

    private static int potionOrder(String key) {
        try {
            Potion potion = BuiltInRegistries.POTION.getValue(Identifier.parse(BrewGraph.potionOf(key)));
            return potion == null ? Integer.MAX_VALUE : BuiltInRegistries.POTION.getId(potion);
        } catch (RuntimeException e) {
            return Integer.MAX_VALUE;
        }
    }

    /** With nothing picked yet, start from the tracked potion or whatever brews in the stand. */
    private void preselectFromStand() {
        if (state.selected != null) {
            return;
        }
        if (state.tracked != null && allPotions.contains(state.tracked)) {
            state.selected = state.tracked;
            return;
        }
        if (parent == null) {
            return;
        }
        for (int i = 0; i < StandHint.INGREDIENT_SLOT; i++) {
            ItemStack stack = parent.getMenu().getSlot(i).getItem();
            if (!stack.isEmpty() && allPotions.contains(BrewingData.keyOf(stack))) {
                state.selected = BrewingData.keyOf(stack);
                return;
            }
        }
    }

    private boolean canBrew(String bottle) {
        return BrewGraph.canBrew(data.routeFromWater(bottle), counts);
    }

    private void filter() {
        String query = state.search.trim().toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>();
        if (state.tab == Tab.POTIONS) {
            for (String bottle : allPotions) {
                if (state.form.accepts(bottle) && (!state.brewableOnly || canBrew(bottle))
                        && (query.isEmpty() || potionMatches(bottle, query))) {
                    out.add(bottle);
                }
            }
        } else {
            for (String reagent : allReagents) {
                String name = PotionText.name(reagentStack(reagent)).toLowerCase(Locale.ROOT);
                if (query.isEmpty() || name.contains(query) || reagent.contains(query)) {
                    out.add(reagent);
                }
            }
        }
        filtered = out;
        gridScroll = 0;
    }

    /** Name, variant or id, or any ingredient on its route ("carrot" finds Night Vision). */
    private boolean potionMatches(String bottle, String query) {
        if (PotionText.full(data.bottle(bottle)).toLowerCase(Locale.ROOT).contains(query) || bottle.contains(query)) {
            return true;
        }
        Route route = data.routeFromWater(bottle);
        if (route != null) {
            for (Edge step : route.steps()) {
                for (String reagent : step.reagents()) {
                    if (PotionText.name(data.reagent(reagent)).toLowerCase(Locale.ROOT).contains(query)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private ItemStack reagentStack(String id) {
        ItemStack stack = data.reagent(id);
        if (stack.isEmpty()) {
            for (ItemStack unknown : data.unknownReagents()) {
                if (BrewingData.idOf(unknown.getItem()).equals(id)) {
                    return unknown;
                }
            }
        }
        return stack;
    }

    // ------------------------------------------------------------------ rows

    /** One entry of the detail list: [badge] [icon] text ... right, with a second line {@code text2} below. */
    private static final class Row {
        boolean header;
        String badge;
        ItemStack icon = ItemStack.EMPTY;
        String text = "";
        int color = MycelTheme.TEXT;
        ItemStack icon2 = ItemStack.EMPTY;
        String text2 = "";
        int color2 = MycelTheme.TEXT;
        String right = "";
        int rightColor = MycelTheme.DIM;
        /** Click target: a bottle to show on the potions page. */
        String bottle;
        /** Click target: a reagent to show on the ingredients page. */
        String reagent;

        static Row header(String text) {
            Row row = new Row();
            row.header = true;
            row.text = text;
            row.color = MycelTheme.DIM;
            return row;
        }

        int height() {
            return header ? HEADER_H : ROW_H;
        }

        static Row note(String text, int color) {
            Row row = new Row();
            row.text = text;
            row.color = color;
            return row;
        }
    }

    private void buildRows() {
        rowScroll = 0;
        List<Row> out = new ArrayList<>();
        if (state.tab == Tab.POTIONS && state.selected != null) {
            potionRows(state.selected, out);
        } else if (state.tab == Tab.INGREDIENTS && state.selectedReagent != null) {
            reagentRows(state.selectedReagent, out);
        }
        rows = out;
    }

    private void potionRows(String bottle, List<Row> out) {
        Route route = data.routeFromWater(bottle);
        Row howTo = Row.header("How to brew");
        howTo.right = "1 ingredient = 3 bottles";
        out.add(howTo);
        if (route == null) {
            out.add(Row.note("No known recipe chain makes this", MycelTheme.BAD));
        } else {
            Row start = new Row();
            start.icon = data.bottle(route.start());
            start.text = "Start: " + PotionText.name(start.icon);
            int bottles = bottleCount(route.start());
            start.right = "have " + bottles;
            start.rightColor = bottles > 0 ? MycelTheme.GOOD : MycelTheme.DIM;
            out.add(start);
            List<String> picks = BrewGraph.pickReagents(route, counts);
            for (int i = 0; i < route.steps().size(); i++) {
                Edge step = route.steps().get(i);
                String pick = picks.get(i);
                String reagent = pick != null ? pick : step.reagents().get(0);
                Row row = new Row();
                row.badge = String.valueOf(i + 1);
                row.icon = data.reagent(reagent);
                row.text = PotionText.name(row.icon) + (step.reagents().size() > 1 ? " (or other)" : "");
                row.color = pick != null ? MycelTheme.GOOD : MycelTheme.BAD;
                row.icon2 = data.bottle(step.to());
                row.text2 = "→ " + PotionText.full(row.icon2);
                int have = counts.getOrDefault(reagent, 0);
                row.right = "×" + have;
                row.rightColor = have > 0 ? MycelTheme.GOOD : MycelTheme.BAD;
                row.reagent = reagent;
                out.add(row);
            }
        }

        List<Edge> next = data.graph().edgesFrom(bottle);
        if (!next.isEmpty()) {
            out.add(Row.header("Brews into"));
            for (Edge edge : next) {
                out.add(linkRow(data.reagent(edge.reagents().get(0)), edge.to(), edge.reagents().get(0)));
            }
        }

        Edge used = route == null || route.steps().isEmpty() ? null : route.steps().get(route.steps().size() - 1);
        List<Row> other = new ArrayList<>();
        for (Edge edge : data.graph().edgesInto(bottle)) {
            if (used != null && edge.from().equals(used.from()) && edge.reagents().equals(used.reagents())) {
                continue;
            }
            Row row = new Row();
            row.icon = data.bottle(edge.from());
            row.text = PotionText.full(row.icon);
            row.icon2 = data.reagent(edge.reagents().get(0));
            row.text2 = "+ " + PotionText.name(row.icon2);
            row.bottle = BrewGraph.isAny(edge.from()) ? null : edge.from();
            other.add(row);
        }
        if (!other.isEmpty()) {
            out.add(Row.header("Also made from"));
            out.addAll(other);
        }
    }

    /** "+ Redstone -> Potion of Night Vision 8:00", clickable to that potion. */
    private Row linkRow(ItemStack reagent, String to, String reagentId) {
        Row row = new Row();
        row.icon = reagent;
        row.text = "+ " + PotionText.name(reagent);
        row.color = counts.getOrDefault(reagentId, 0) > 0 ? MycelTheme.TEXT : MycelTheme.DIM;
        row.icon2 = data.bottle(to);
        row.text2 = "→ " + PotionText.full(row.icon2);
        row.bottle = to;
        return row;
    }

    private void reagentRows(String reagent, List<Row> out) {
        List<Edge> uses = new ArrayList<>(data.graph().edgesUsing(reagent));
        if (uses.isEmpty()) {
            out.add(Row.header("Unknown recipes"));
            out.add(Row.note("This server accepts it in the stand,", MycelTheme.TEXT));
            out.add(Row.note("but servers don't share brewing recipes.", MycelTheme.TEXT));
            out.add(Row.note("Try it on an Awkward Potion first.", MycelTheme.DIM));
            return;
        }
        uses.sort(Comparator.comparingInt((Edge e) -> formOrder(e.from()))
                .thenComparingInt(e -> potionOrder(e.from()))
                .thenComparingInt(e -> potionOrder(e.to())));
        out.add(Row.header("Used in " + uses.size() + (uses.size() == 1 ? " recipe" : " recipes")));
        for (Edge edge : uses) {
            Row row = new Row();
            row.icon = data.bottle(edge.from());
            row.text = BrewGraph.isAny(edge.from()) ? "Any " + PotionText.name(row.icon) : PotionText.full(row.icon);
            row.icon2 = data.bottle(edge.to());
            row.text2 = "→ " + PotionText.full(row.icon2);
            row.bottle = edge.to();
            out.add(row);
        }
    }

    // ---------------------------------------------------------------- widgets

    @Override
    protected void init() {
        int buttonY = height - FOOTER + 6;
        int searchWidth = leftEnd() - MARGIN - (state.tab == Tab.POTIONS ? haveWidth() + 4 : 0);
        search = new EditBox(font, MARGIN, SEARCH_Y, searchWidth, 14, Component.literal("Search"));
        search.setHint(Component.literal(state.tab == Tab.POTIONS ? "Potion or ingredient…" : "Ingredient…"));
        search.setValue(state.search);
        search.setResponder(text -> {
            state.search = text;
            filter();
        });
        addRenderableWidget(search);
        if (state.tab == Tab.POTIONS) {
            trackButton = Button.builder(trackLabel(), button -> toggleTrack())
                    .bounds(width - MARGIN - 100 - 4 - 104, buttonY, 104, 20).build();
            trackButton.active = state.selected != null;
            addRenderableWidget(trackButton);
        } else {
            trackButton = null;
        }
        String back = parent != null ? "Back to stand" : "Done";
        addRenderableWidget(Button.builder(Component.literal(back), button -> onClose())
                .bounds(width - MARGIN - 100, buttonY, 100, 20).build());
    }

    private Component trackLabel() {
        boolean tracking = state.selected != null && state.selected.equals(state.tracked);
        return Component.literal(tracking ? "Stop tracking" : "Track at stand");
    }

    private void toggleTrack() {
        state.tracked = state.selected != null && state.selected.equals(state.tracked) ? null : state.selected;
        BrewingPlusMod.debug("Tracking {}", state.tracked);
        if (trackButton != null) {
            trackButton.setMessage(trackLabel());
        }
    }

    @Override
    protected void setInitialFocus() {
        if (search != null) {
            setInitialFocus(search);
        } else {
            super.setInitialFocus();
        }
    }

    private void showTab(Tab tab) {
        if (state.tab == tab) {
            return;
        }
        BrewingPlusMod.debug("Tab -> {}", tab);
        state.tab = tab;
        state.search = "";
        filter();
        buildRows();
        rebuildWidgets();
    }

    private void select(String bottle) {
        BrewingPlusMod.debug("Selected potion {}", bottle);
        state.selected = bottle;
        if (state.tab != Tab.POTIONS) {
            state.tab = Tab.POTIONS;
            state.search = "";
            filter();
            rebuildWidgets();
        }
        buildRows();
        if (trackButton != null) {
            trackButton.active = true;
            trackButton.setMessage(trackLabel());
        }
        scrollGridTo(bottle);
    }

    private void selectReagent(String reagent) {
        BrewingPlusMod.debug("Selected reagent {}", reagent);
        state.selectedReagent = reagent;
        if (state.tab != Tab.INGREDIENTS) {
            state.tab = Tab.INGREDIENTS;
            state.search = "";
            filter();
            rebuildWidgets();
        }
        buildRows();
        scrollGridTo(reagent);
    }

    /** Keeps a selection made from the detail list visible in the grid. */
    private void scrollGridTo(String key) {
        int index = filtered.indexOf(key);
        if (index < 0) {
            return;
        }
        int row = index / columns();
        if (row < gridScroll || row >= gridScroll + gridRows()) {
            gridScroll = Math.max(0, Math.min(maxGridScroll(), row - gridRows() / 2));
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    /** Back to the stand if it is still open server-side, otherwise to the game. */
    @Override
    public void onClose() {
        LocalPlayer player = minecraft.player;
        boolean standOpen = parent != null && player != null && player.containerMenu == parent.getMenu();
        BrewingPlusMod.debug("Guide closed, returning to {}", standOpen ? "stand" : "game");
        MycelScreens.open(standOpen ? parent : null);
    }

    // ---------------------------------------------------------------- layout

    /** Where the grid panel ends and the detail panel starts. */
    private int split() {
        return width * 9 / 20;
    }

    private int leftEnd() {
        return split() - 8;
    }

    private int gridTop() {
        return state.tab == Tab.POTIONS ? CHIP_Y + CHIP_H + 6 : CHIP_Y;
    }

    private int columns() {
        return Math.max(1, (leftEnd() - MARGIN - 4) / CELL);
    }

    private int gridLeft() {
        return MARGIN + (leftEnd() - MARGIN - 4 - columns() * CELL) / 2;
    }

    private int gridRows() {
        return Math.max(1, (height - FOOTER - gridTop() - 4) / CELL);
    }

    private int maxGridScroll() {
        int total = (filtered.size() + columns() - 1) / columns();
        return Math.max(0, total - gridRows());
    }

    private int detailLeft() {
        return split() + 7;
    }

    private int detailRight() {
        return width - MARGIN - 4;
    }

    private int summaryTop() {
        return height - FOOTER - 2 - SUMMARY_LINES * LINE_H - 4;
    }

    private int detailBottom() {
        return state.tab == Tab.POTIONS && state.selected != null ? summaryTop() - 4 : height - FOOTER - 2;
    }

    /** How many rows from {@code first} fit above the summary. */
    private int rowsFitting(int first) {
        int y = DETAIL_TOP;
        int count = 0;
        for (int i = first; i < rows.size(); i++) {
            y += rows.get(i).height();
            if (y > detailBottom()) {
                break;
            }
            count++;
        }
        return Math.max(1, count);
    }

    /** The first scroll position from which every remaining row fits. */
    private int maxRowScroll() {
        int y = detailBottom();
        int scroll = rows.size();
        while (scroll > 0 && y - rows.get(scroll - 1).height() >= DETAIL_TOP) {
            y -= rows.get(scroll - 1).height();
            scroll--;
        }
        return scroll;
    }

    /** Index of the row under {@code my}, or -1. */
    private int rowAt(double my) {
        int y = DETAIL_TOP;
        for (int i = rowScroll; i < rowScroll + rowsFitting(rowScroll); i++) {
            int h = rows.get(i).height();
            if (my >= y && my < y + h) {
                return i;
            }
            y += h;
        }
        return -1;
    }

    // ---------------------------------------------------------------- render

    @Override
    public void extractRenderState(GuiGraphicsExtractor gfx, int mouseX, int mouseY, float a) {
        // Chrome first so widgets (search box, buttons) draw on top of the panels.
        gfx.fill(0, 0, width, height, MycelTheme.SHADE);
        gfx.fill(0, 0, width, TITLE_BAR, MycelTheme.ACCENT_DARK);
        gfx.fill(0, TITLE_BAR - 2, width, TITLE_BAR, MycelTheme.HEADER_EDGE);
        gfx.centeredText(font, title, width / 2, 4, MycelTheme.HEADING);
        String source = data.source() == BrewingData.Source.WORLD ? "Recipes: this world" : "Recipes: vanilla";
        gfx.text(font, source, width - MARGIN - font.width(source), 4, 0xFFFFCDD2);
        MycelTheme.panel(gfx, 4, TITLE_BAR + 2, split() - 1, height - FOOTER + 2);
        MycelTheme.panel(gfx, split() + 1, TITLE_BAR + 2, width - 4, height - FOOTER + 2);
        super.extractRenderState(gfx, mouseX, mouseY, a);
        renderTabs(gfx, mouseX, mouseY);
        if (state.tab == Tab.POTIONS) {
            renderChips(gfx, mouseX, mouseY);
            renderHaveToggle(gfx, mouseX, mouseY);
        }
        renderGrid(gfx, mouseX, mouseY);
        renderDetail(gfx, mouseX, mouseY);
    }

    private int tabWidth() {
        return (leftEnd() - MARGIN) / 2 - 2;
    }

    private void renderTabs(GuiGraphicsExtractor gfx, int mouseX, int mouseY) {
        Tab[] tabs = Tab.values();
        for (int i = 0; i < tabs.length; i++) {
            int x = MARGIN + i * (tabWidth() + 4);
            boolean on = state.tab == tabs[i];
            boolean hover = inside(mouseX, mouseY, x, TAB_Y, tabWidth(), TAB_H);
            gfx.fill(x, TAB_Y, x + tabWidth(), TAB_Y + TAB_H, on ? MycelTheme.ACCENT_DARK : hover ? MycelTheme.BORDER : MycelTheme.SURFACE);
            String label = tabs[i] == Tab.POTIONS ? "Potions (" + allPotions.size() + ")"
                    : "Ingredients (" + allReagents.size() + ")";
            gfx.centeredText(font, MycelTheme.fit(font, label, tabWidth() - 4), x + tabWidth() / 2, TAB_Y + 3,
                    on ? MycelTheme.HEADING : MycelTheme.DIM);
        }
    }

    static final String HAVE_LABEL = "Have all";
    static final int HAVE_ON = 0xFF1F4D2B;

    /** Chip x positions, one per form. */
    private int[] chipXs() {
        Form[] forms = Form.values();
        int[] xs = new int[forms.length];
        int x = MARGIN;
        for (int i = 0; i < forms.length; i++) {
            xs[i] = x;
            x += font.width(forms[i].label) + 8 + 3;
        }
        return xs;
    }

    private void renderChips(GuiGraphicsExtractor gfx, int mouseX, int mouseY) {
        Form[] forms = Form.values();
        int[] xs = chipXs();
        for (int i = 0; i < forms.length; i++) {
            int w = font.width(forms[i].label) + 8;
            if (xs[i] + w > leftEnd()) {
                break;
            }
            boolean on = state.form == forms[i];
            boolean hover = inside(mouseX, mouseY, xs[i], CHIP_Y, w, CHIP_H);
            gfx.fill(xs[i], CHIP_Y, xs[i] + w, CHIP_Y + CHIP_H, on ? MycelTheme.ACCENT_DARK : hover ? MycelTheme.BORDER : MycelTheme.SURFACE);
            gfx.text(font, forms[i].label, xs[i] + 4, CHIP_Y + 2, on ? MycelTheme.HEADING : MycelTheme.DIM);
        }
    }

    private int haveWidth() {
        return font.width(HAVE_LABEL) + 10;
    }

    private int haveX() {
        return leftEnd() - haveWidth();
    }

    /** "Have all": only potions you carry every ingredient for. */
    private void renderHaveToggle(GuiGraphicsExtractor gfx, int mouseX, int mouseY) {
        boolean hover = inside(mouseX, mouseY, haveX(), SEARCH_Y, haveWidth(), 14);
        gfx.fill(haveX(), SEARCH_Y, leftEnd(), SEARCH_Y + 14,
                state.brewableOnly ? HAVE_ON : hover ? MycelTheme.BORDER : MycelTheme.SURFACE);
        gfx.fill(haveX() + 3, SEARCH_Y + 5, haveX() + 6, SEARCH_Y + 8, state.brewableOnly ? MycelTheme.GOOD : MycelTheme.OFF);
        gfx.text(font, HAVE_LABEL, haveX() + 8, SEARCH_Y + 3, state.brewableOnly ? MycelTheme.HEADING : MycelTheme.DIM);
        if (hover) {
            gfx.setTooltipForNextFrame(font, Component.literal("Only potions you carry every ingredient for"),
                    mouseX, mouseY);
        }
    }

    private void renderGrid(GuiGraphicsExtractor gfx, int mouseX, int mouseY) {
        if (filtered.isEmpty()) {
            String empty = state.brewableOnly ? "Nothing you can brew yet" : "Nothing matches";
            gfx.text(font, MycelTheme.fit(font, empty, leftEnd() - MARGIN), MARGIN, gridTop() + 4, MycelTheme.DIM);
            return;
        }
        gridScroll = Math.min(gridScroll, maxGridScroll());
        int hovered = cellAt(mouseX, mouseY);
        for (int row = 0; row < gridRows(); row++) {
            for (int col = 0; col < columns(); col++) {
                int index = (row + gridScroll) * columns() + col;
                if (index >= filtered.size()) {
                    break;
                }
                String key = filtered.get(index);
                int x = gridLeft() + col * CELL;
                int y = gridTop() + row * CELL;
                boolean potions = state.tab == Tab.POTIONS;
                boolean picked = key.equals(potions ? state.selected : state.selectedReagent);
                boolean special = potions ? key.equals(state.tracked) : data.reagent(key).isEmpty();
                gfx.fill(x, y, x + CELL - 1, y + CELL - 1, picked || index == hovered ? MycelTheme.ACCENT : MycelTheme.BORDER);
                gfx.fill(x + 1, y + 1, x + CELL - 2, y + CELL - 2, picked || special ? MycelTheme.NOTE : MycelTheme.SURFACE);
                ItemStack stack = potions ? data.bottle(key) : reagentStack(key);
                gfx.item(stack, x + 1, y + 1);
                boolean ready = potions ? canBrew(key) : counts.getOrDefault(key, 0) > 0;
                if (ready) {
                    gfx.fill(x + CELL - 5, y + CELL - 5, x + CELL - 2, y + CELL - 2, MycelTheme.GOOD);
                }
                if (!potions && data.reagent(key).isEmpty()) {
                    gfx.text(font, "?", x + 2, y + 1, MycelTheme.WARN);
                }
            }
        }
        if (hovered >= 0) {
            String key = filtered.get(hovered);
            ItemStack stack = state.tab == Tab.POTIONS ? data.bottle(key) : reagentStack(key);
            gfx.setTooltipForNextFrame(font, stack, mouseX, mouseY);
        }
        drawScrollbar(gfx, gridLeft() + columns() * CELL + 2, gridTop(), gridRows() * CELL, gridScroll, maxGridScroll());
    }

    private int cellAt(double mouseX, double mouseY) {
        int col = (int) Math.floor((mouseX - gridLeft()) / CELL);
        int row = (int) Math.floor((mouseY - gridTop()) / CELL);
        if (mouseX < gridLeft() || mouseY < gridTop() || col >= columns() || row >= gridRows()) {
            return -1;
        }
        int index = (row + gridScroll) * columns() + col;
        return index < filtered.size() ? index : -1;
    }

    private void renderDetail(GuiGraphicsExtractor gfx, int mouseX, int mouseY) {
        int x0 = detailLeft();
        int x1 = detailRight();
        gfx.fill(split() + 3, TITLE_BAR + 4, width - 6, DETAIL_TOP - 6, MycelTheme.SURFACE);
        boolean potions = state.tab == Tab.POTIONS;
        String key = potions ? state.selected : state.selectedReagent;
        if (key == null) {
            int mid = (x0 + x1) / 2;
            int y = (DETAIL_TOP + height - FOOTER) / 2 - 12;
            gfx.text(font, potions ? "Potion guide" : "Ingredient guide", x0, TAB_Y + 5, MycelTheme.HEADING);
            gfx.centeredText(font, potions ? "Pick a potion on the left" : "Pick an ingredient on the left",
                    mid, y, MycelTheme.TEXT);
            gfx.centeredText(font, MycelTheme.fit(font, potions ? "Green dot: you can brew it now"
                    : "Green dot: you have some", x1 - x0), mid, y + 12, MycelTheme.DIM);
            return;
        }
        ItemStack icon = potions ? data.bottle(key) : reagentStack(key);
        gfx.item(icon, x0, TAB_Y + 1);
        gfx.text(font, MycelTheme.fit(font, potions ? PotionText.full(icon) : PotionText.name(icon), x1 - x0 - 20),
                x0 + 20, TAB_Y + 1, MycelTheme.HEADING);
        String sub;
        if (potions) {
            Route route = data.routeFromWater(key);
            sub = route == null ? "Can't be brewed from water"
                    : route.steps().size() + (route.steps().size() == 1 ? " step" : " steps") + " from "
                            + PotionText.name(data.bottle(route.start()));
        } else {
            sub = "You have " + counts.getOrDefault(key, 0);
        }
        gfx.text(font, MycelTheme.fit(font, sub, x1 - x0 - 20), x0 + 20, TAB_Y + 11, MycelTheme.DIM);
        if (inside(mouseX, mouseY, x0, TAB_Y + 1, 16, 16)) {
            gfx.setTooltipForNextFrame(font, icon, mouseX, mouseY);
        }

        rowScroll = Math.min(rowScroll, maxRowScroll());
        ItemStack hoveredIcon = ItemStack.EMPTY;
        int y = DETAIL_TOP;
        for (int i = rowScroll; i < rowScroll + rowsFitting(rowScroll) && i < rows.size(); i++) {
            Row row = rows.get(i);
            ItemStack hit = renderRow(gfx, row, i, x0, x1, y, mouseX, mouseY);
            if (!hit.isEmpty()) {
                hoveredIcon = hit;
            }
            y += row.height();
        }
        drawScrollbar(gfx, x1 + 2, DETAIL_TOP, detailBottom() - DETAIL_TOP, rowScroll, maxRowScroll());
        if (potions) {
            renderSummary(gfx, key, x0, x1);
        }
        if (!hoveredIcon.isEmpty()) {
            gfx.setTooltipForNextFrame(font, hoveredIcon, mouseX, mouseY);
        }
    }

    /** Draws one detail row; returns the item under the mouse, if any. */
    private ItemStack renderRow(GuiGraphicsExtractor gfx, Row row, int index, int x0, int x1, int y, int mouseX,
            int mouseY) {
        if (row.header) {
            gfx.text(font, row.text, x0, y + 3, row.color);
            if (!row.right.isEmpty()) {
                gfx.text(font, row.right, x1 - font.width(row.right), y + 3, MycelTheme.OFF);
            }
            gfx.fill(x0, y + 12, x1, y + 13, MycelTheme.BORDER);
            return ItemStack.EMPTY;
        }
        int h = row.height();
        boolean clickable = row.bottle != null || row.reagent != null;
        boolean hovered = inside(mouseX, mouseY, x0 - 2, y, x1 - x0 + 4, h);
        if (index % 2 == 1) {
            gfx.fill(x0 - 2, y, x1 + 2, y + h, MycelTheme.ZEBRA);
        }
        if (hovered && clickable) {
            gfx.fill(x0 - 2, y, x1 + 2, y + h, MycelTheme.SURFACE);
        }
        int x = x0;
        if (row.badge != null) {
            int badge = Math.max(10, font.width(row.badge) + 4);
            gfx.fill(x, y + 6, x + badge, y + 16, MycelTheme.ACCENT_DARK);
            gfx.centeredText(font, row.badge, x + badge / 2 + 1, y + 7, MycelTheme.HEADING);
            x += badge + 3;
        }
        ItemStack hit = ItemStack.EMPTY;
        if (!row.icon.isEmpty()) {
            gfx.item(row.icon, x, y + 3);
            if (inside(mouseX, mouseY, x, y + 3, 16, 16)) {
                hit = row.icon;
            }
            x += 19;
        }
        int rightWidth = row.right.isEmpty() ? 0 : font.width(row.right) + 4;
        boolean twoLines = !row.text2.isEmpty();
        int line1 = twoLines ? y + 2 : y + 7;
        gfx.text(font, MycelTheme.fit(font, row.text, x1 - rightWidth - x), x, line1, row.color);
        if (!row.right.isEmpty()) {
            gfx.text(font, row.right, x1 - font.width(row.right), line1, row.rightColor);
        }
        if (twoLines) {
            gfx.text(font, MycelTheme.fit(font, row.text2, x1 - x), x, y + 12, row.color2);
            if (!row.icon2.isEmpty() && inside(mouseX, mouseY, x, y + 11, x1 - x, 10)) {
                hit = row.icon2;
            }
        }
        return hit;
    }

    /** "You carry everything" / "Missing: ...", the 3-bottle note, and the stand's fuel. */
    private void renderSummary(GuiGraphicsExtractor gfx, String bottle, int x0, int x1) {
        int y = summaryTop();
        gfx.fill(x0 - 2, y - 3, x1 + 2, y - 2, MycelTheme.BORDER);
        Route route = data.routeFromWater(bottle);
        if (route == null) {
            return;
        }
        List<String> picks = BrewGraph.pickReagents(route, counts);
        Set<String> missing = new LinkedHashSet<>();
        for (int i = 0; i < picks.size(); i++) {
            if (picks.get(i) == null) {
                missing.add(PotionText.name(data.reagent(route.steps().get(i).reagents().get(0))));
            }
        }
        gfx.fill(x0 - 2, y, x0 + 1, y + LINE_H - 1, missing.isEmpty() ? MycelTheme.GOOD : MycelTheme.ACCENT);
        String status = missing.isEmpty() ? "You carry everything for this"
                : "Missing: " + String.join(", ", missing);
        gfx.text(font, MycelTheme.fit(font, status, x1 - x0 - 6), x0 + 5, y + 1, missing.isEmpty() ? MycelTheme.GOOD : MycelTheme.BAD);
        y += LINE_H;
        if (parent != null) {
            int fuel = parent.getMenu().getFuel();
            int steps = route.steps().size();
            String line = fuel > 0 ? "Stand fuel: " + fuel + (fuel == 1 ? " brew" : " brews") + " left"
                    : "Stand has no fuel: add Blaze Powder";
            int color = fuel <= 0 ? MycelTheme.WARN : fuel < steps ? MycelTheme.WARN : MycelTheme.DIM;
            gfx.text(font, MycelTheme.fit(font, line, x1 - x0), x0, y + 1, color);
        }
    }

    private void drawScrollbar(GuiGraphicsExtractor gfx, int x, int top, int height, int scroll, int maxScroll) {
        if (maxScroll <= 0) {
            return;
        }
        int thumb = Math.max(8, height / (maxScroll + 1));
        int y = top + (height - thumb) * scroll / maxScroll;
        gfx.fill(x, top, x + 2, top + height, MycelTheme.SURFACE);
        gfx.fill(x, y, x + 2, y + thumb, MycelTheme.OFF);
    }

    private static boolean inside(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    // ----------------------------------------------------------------- input

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (super.mouseClicked(event, doubleClick)) {
            return true;
        }
        // 26.x mouse input is SDL-based: left is 1 there, not GLFW's 0.
        if (event.button() != InputConstants.MOUSE_BUTTON_LEFT) {
            return false;
        }
        double mx = event.x();
        double my = event.y();
        Tab[] tabs = Tab.values();
        for (int i = 0; i < tabs.length; i++) {
            if (inside(mx, my, MARGIN + i * (tabWidth() + 4), TAB_Y, tabWidth(), TAB_H)) {
                showTab(tabs[i]);
                return true;
            }
        }
        if (state.tab == Tab.POTIONS && clickChip(mx, my)) {
            return true;
        }
        if (state.tab == Tab.POTIONS && inside(mx, my, haveX(), SEARCH_Y, haveWidth(), 14)) {
            state.brewableOnly = !state.brewableOnly;
            BrewingPlusMod.debug("Have all -> {}", state.brewableOnly);
            filter();
            return true;
        }
        int cell = cellAt(mx, my);
        if (cell >= 0) {
            String key = filtered.get(cell);
            if (state.tab == Tab.POTIONS) {
                select(key);
            } else {
                selectReagent(key);
            }
            return true;
        }
        if (mx >= detailLeft() - 2 && mx < detailRight() + 2 && my >= DETAIL_TOP) {
            int index = rowAt(my);
            if (index >= 0) {
                Row row = rows.get(index);
                if (row.bottle != null && allPotions.contains(row.bottle)) {
                    select(row.bottle);
                    return true;
                }
                if (row.reagent != null) {
                    selectReagent(row.reagent);
                    return true;
                }
            }
        }
        return false;
    }

    private boolean clickChip(double mx, double my) {
        Form[] forms = Form.values();
        int[] xs = chipXs();
        for (int i = 0; i < forms.length; i++) {
            if (inside(mx, my, xs[i], CHIP_Y, font.width(forms[i].label) + 8, CHIP_H)) {
                state.form = forms[i];
                BrewingPlusMod.debug("Filter -> form {}", state.form);
                filter();
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
        if (super.mouseScrolled(x, y, scrollX, scrollY)) {
            return true;
        }
        int delta = scrollY > 0 ? -1 : scrollY < 0 ? 1 : 0;
        if (x < split()) {
            gridScroll = Math.max(0, Math.min(maxGridScroll(), gridScroll + delta));
        } else {
            rowScroll = Math.max(0, Math.min(maxRowScroll(), rowScroll + delta));
        }
        return true;
    }
}
