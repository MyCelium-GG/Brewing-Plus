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
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Checkbox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.BrewingStandScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderPipelines;
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
    static final int MAX_W = 360;
    static final int MAX_H = 232;
    static final int PAD = 8;
    static final int CELL = 18;
    static final int MAX_COLUMNS = 8;
    static final int ROW_H = 22;
    static final int HEADER_H = 14;
    static final int LINE_H = 11;
    static final int SUMMARY_LINES = 2;
    static final int BUTTON_H = 20;
    /** Width of vanilla's list scroller ({@code widget/scroller}). */
    static final int THIN_SCROLLER = 6;

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
    private Button formButton;

    /** The window, centred and sized to the screen in {@link #init()}. */
    private int px;
    private int py;
    private int pw;
    private int ph;
    private int cols = MAX_COLUMNS;

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
        int color = VanillaUi.LABEL;
        ItemStack icon2 = ItemStack.EMPTY;
        String text2 = "";
        int color2 = VanillaUi.LABEL;
        String right = "";
        int rightColor = VanillaUi.DIM;
        /** Click target: a bottle to show on the potions page. */
        String bottle;
        /** Click target: a reagent to show on the ingredients page. */
        String reagent;

        static Row header(String text) {
            Row row = new Row();
            row.header = true;
            row.text = text;
            row.color = VanillaUi.DIM;
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
        Row howTo = Row.header(Tr.s("guide.how"));
        howTo.right = Tr.s("guide.three_bottles");
        out.add(howTo);
        if (route == null) {
            out.add(Row.note(Tr.s("guide.no_chain"), VanillaUi.BAD));
        } else {
            Row start = new Row();
            start.icon = data.bottle(route.start());
            start.text = Tr.s("guide.start", PotionText.name(start.icon));
            int bottles = bottleCount(route.start());
            start.right = Tr.s("guide.have", bottles);
            start.rightColor = bottles > 0 ? VanillaUi.GOOD : VanillaUi.DIM;
            out.add(start);
            List<String> picks = BrewGraph.pickReagents(route, counts);
            for (int i = 0; i < route.steps().size(); i++) {
                Edge step = route.steps().get(i);
                String pick = picks.get(i);
                String reagent = pick != null ? pick : step.reagents().get(0);
                Row row = new Row();
                row.badge = String.valueOf(i + 1);
                row.icon = data.reagent(reagent);
                row.text = PotionText.name(row.icon) + (step.reagents().size() > 1 ? " (" + Tr.s("guide.or_other") + ")" : "");
                row.color = pick != null ? VanillaUi.GOOD : VanillaUi.BAD;
                row.icon2 = data.bottle(step.to());
                row.text2 = "→ " + PotionText.full(row.icon2);
                int have = counts.getOrDefault(reagent, 0);
                row.right = "×" + have;
                row.rightColor = have > 0 ? VanillaUi.GOOD : VanillaUi.BAD;
                row.reagent = reagent;
                out.add(row);
            }
        }

        List<Edge> next = data.graph().edgesFrom(bottle);
        if (!next.isEmpty()) {
            out.add(Row.header(Tr.s("guide.brews_into")));
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
            out.add(Row.header(Tr.s("guide.also_from")));
            out.addAll(other);
        }
    }

    /** "+ Redstone -> Potion of Night Vision 8:00", clickable to that potion. */
    private Row linkRow(ItemStack reagent, String to, String reagentId) {
        Row row = new Row();
        row.icon = reagent;
        row.text = "+ " + PotionText.name(reagent);
        row.color = counts.getOrDefault(reagentId, 0) > 0 ? VanillaUi.LABEL : VanillaUi.DIM;
        row.icon2 = data.bottle(to);
        row.text2 = "→ " + PotionText.full(row.icon2);
        row.bottle = to;
        return row;
    }

    private void reagentRows(String reagent, List<Row> out) {
        List<Edge> uses = new ArrayList<>(data.graph().edgesUsing(reagent));
        if (uses.isEmpty()) {
            out.add(Row.header(Tr.s("guide.unknown")));
            out.add(Row.note(Tr.s("guide.unknown.1"), VanillaUi.LABEL));
            out.add(Row.note(Tr.s("guide.unknown.2"), VanillaUi.LABEL));
            out.add(Row.note(Tr.s("guide.unknown.3"), VanillaUi.DIM));
            return;
        }
        uses.sort(Comparator.comparingInt((Edge e) -> formOrder(e.from()))
                .thenComparingInt(e -> potionOrder(e.from()))
                .thenComparingInt(e -> potionOrder(e.to())));
        out.add(Row.header(Tr.plural(uses.size(), "guide.used_in")));
        for (Edge edge : uses) {
            Row row = new Row();
            row.icon = data.bottle(edge.from());
            row.text = BrewGraph.isAny(edge.from()) ? Tr.s("guide.any", PotionText.name(row.icon)) : PotionText.full(row.icon);
            row.icon2 = data.bottle(edge.to());
            row.text2 = "→ " + PotionText.full(row.icon2);
            row.bottle = edge.to();
            out.add(row);
        }
    }


    // ---------------------------------------------------------------- layout

    private int leftX() {
        return px + PAD;
    }

    /** Grid plus the creative-style scrollbar beside it. */
    private int leftWidth() {
        return cols * CELL + 2 + VanillaUi.SCROLLER_W + 2;
    }

    private int tabY() {
        return py + 17;
    }

    private int searchY() {
        return tabY() + BUTTON_H + 4;
    }

    private int filterY() {
        return searchY() + 18;
    }

    private int gridTop() {
        return state.tab == Tab.POTIONS ? filterY() + 21 : filterY();
    }

    private int columns() {
        return cols;
    }

    private int gridLeft() {
        return leftX();
    }

    private int gridRows() {
        return Math.max(1, (py + ph - PAD - gridTop()) / CELL);
    }

    private int maxGridScroll() {
        int total = (filtered.size() + columns() - 1) / columns();
        return Math.max(0, total - gridRows());
    }

    private int rightX() {
        return leftX() + leftWidth() + PAD;
    }

    private int rightEnd() {
        return px + pw - PAD;
    }

    private int boxTop() {
        return tabY();
    }

    private int boxBottom() {
        return py + ph - PAD - BUTTON_H - 4;
    }

    private int detailLeft() {
        return rightX() + 4;
    }

    private int detailRight() {
        return rightEnd() - 4 - THIN_SCROLLER - 2;
    }

    private int detailTop() {
        return boxTop() + 26;
    }

    private int summaryTop() {
        return boxBottom() - 3 - SUMMARY_LINES * LINE_H;
    }

    private int detailBottom() {
        return state.tab == Tab.POTIONS && state.selected != null ? summaryTop() - 4 : boxBottom() - 3;
    }

    /** How many rows from {@code first} fit above the summary. */
    private int rowsFitting(int first) {
        int y = detailTop();
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
        while (scroll > 0 && y - rows.get(scroll - 1).height() >= detailTop()) {
            y -= rows.get(scroll - 1).height();
            scroll--;
        }
        return scroll;
    }

    /** Index of the row under {@code my}, or -1. */
    private int rowAt(double my) {
        int y = detailTop();
        for (int i = rowScroll; i < rowScroll + rowsFitting(rowScroll) && i < rows.size(); i++) {
            int h = rows.get(i).height();
            if (my >= y && my < y + h) {
                return i;
            }
            y += h;
        }
        return -1;
    }

    // ---------------------------------------------------------------- widgets

    @Override
    protected void init() {
        pw = Math.min(MAX_W, width - 8);
        ph = Math.min(MAX_H, height - 8);
        px = (width - pw) / 2;
        py = (height - ph) / 2;
        cols = Math.max(4, Math.min(MAX_COLUMNS, (pw * 46 / 100 - 2 * PAD - VanillaUi.SCROLLER_W - 4) / CELL));

        int lw = leftWidth();
        int tabW = (lw - 2) / 2;
        Tab[] tabs = Tab.values();
        for (int i = 0; i < tabs.length; i++) {
            Tab tab = tabs[i];
            String label = tab == Tab.POTIONS ? Tr.s("tab.potions", allPotions.size())
                    : Tr.s("tab.ingredients", allReagents.size());
            Button button = Button.builder(Component.literal(label), b -> showTab(tab))
                    .bounds(leftX() + i * (tabW + 2), tabY(), i == 0 ? tabW : lw - tabW - 2, BUTTON_H).build();
            button.active = state.tab != tab;
            addRenderableWidget(button);
        }

        search = new EditBox(font, leftX() + 1, searchY(), lw - 2, 14, Component.literal(Tr.s("search")));
        search.setHint(Component.literal(VanillaUi.fit(font,
                Tr.s(state.tab == Tab.POTIONS ? "search.potions" : "search.ingredients"), lw - 10)));
        search.setValue(state.search);
        search.setResponder(text -> {
            state.search = text;
            filter();
        });
        addRenderableWidget(search);

        if (state.tab == Tab.POTIONS) {
            int box = Checkbox.getBoxSize(font);
            int formW = Math.max(54, formLabelWidth() + 10);
            formButton = Button.builder(Component.literal(Tr.s("form." + state.form.label)), b -> cycleForm())
                    .bounds(leftX(), filterY(), formW, box).build();
            formButton.setTooltip(Tooltip.create(Component.literal(formTooltip())));
            addRenderableWidget(formButton);
            Checkbox have = Checkbox.builder(Component.empty(), font)
                    .pos(leftX() + formW + 6, filterY())
                    .selected(state.brewableOnly)
                    .tooltip(Tooltip.create(Component.literal(Tr.s("have_all.tip"))))
                    .onValueChange((checkbox, value) -> {
                        state.brewableOnly = value;
                        BrewingPlusMod.debug("Have all -> {}", value);
                        filter();
                    })
                    .build();
            addRenderableWidget(have);
        } else {
            formButton = null;
        }

        int buttonY = py + ph - PAD - BUTTON_H;
        int rw = rightEnd() - rightX();
        int half = (rw - 4) / 2;
        if (state.tab == Tab.POTIONS) {
            trackButton = Button.builder(trackLabel(), button -> toggleTrack())
                    .bounds(rightX(), buttonY, half, BUTTON_H).build();
            trackButton.active = state.selected != null;
            addRenderableWidget(trackButton);
        } else {
            trackButton = null;
        }
        Component back = parent != null ? Component.literal(Tr.s("button.back")) : Component.translatable("gui.done");
        addRenderableWidget(Button.builder(back, button -> onClose())
                .bounds(rightEnd() - half, buttonY, half, BUTTON_H).build());
    }

    private int formLabelWidth() {
        int w = 0;
        for (Form form : Form.values()) {
            w = Math.max(w, font.width(Tr.s("form." + form.label)));
        }
        return w;
    }

    /** "All / Potion / Splash / Lingering" with the current one marked. */
    private String formTooltip() {
        StringBuilder out = new StringBuilder();
        for (Form form : Form.values()) {
            if (!out.isEmpty()) {
                out.append('\n');
            }
            out.append(form == state.form ? "> " : "   ").append(Tr.s("form." + form.label));
        }
        return out.toString();
    }

    private void cycleForm() {
        Form[] forms = Form.values();
        state.form = forms[(state.form.ordinal() + 1) % forms.length];
        BrewingPlusMod.debug("Filter -> form {}", state.form);
        if (formButton != null) {
            formButton.setMessage(Component.literal(Tr.s("form." + state.form.label)));
            formButton.setTooltip(Tooltip.create(Component.literal(formTooltip())));
        }
        filter();
    }

    private Component trackLabel() {
        boolean tracking = state.selected != null && state.selected.equals(state.tracked);
        return Component.literal(Tr.s(tracking ? "button.untrack" : "button.track"));
    }

    private void toggleTrack() {
        state.tracked = state.selected != null && state.selected.equals(state.tracked) ? null : state.selected;
        BrewingPlusMod.debug("Tracking {}", state.tracked);
        if (state.tracked != null) {
            my.celium.org.brewingplus.BrewingStats.count("track");
            my.celium.org.brewingplus.BrewingStats.count("tracked_potions", state.tracked);
        }
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
        my.celium.org.brewingplus.BrewingStats.count("looked_up_potions", bottle);
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
        my.celium.org.brewingplus.BrewingStats.count("looked_up_ingredients", reagent);
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

    // ---------------------------------------------------------------- render

    /** The world (dimmed and blurred as usual), then the grey window under the widgets. */
    @Override
    public void extractBackground(GuiGraphicsExtractor gfx, int mouseX, int mouseY, float a) {
        super.extractBackground(gfx, mouseX, mouseY, a);
        VanillaUi.panel(gfx, px, py, pw, ph);
        VanillaUi.label(gfx, font, title.getString(), leftX(), py + 6, VanillaUi.LABEL, leftWidth());
        String source = Tr.s(data.source() == BrewingData.Source.WORLD ? "source.world" : "source.vanilla");
        source = VanillaUi.fit(font, source, rightEnd() - rightX());
        gfx.text(font, source, rightEnd() - font.width(source), py + 6, VanillaUi.DIM, false);
        VanillaUi.inset(gfx, rightX(), boxTop(), rightEnd() - rightX(), boxBottom() - boxTop(), VanillaUi.BODY);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor gfx, int mouseX, int mouseY, float a) {
        super.extractRenderState(gfx, mouseX, mouseY, a);
        if (state.tab == Tab.POTIONS) {
            int x = leftX() + Math.max(54, formLabelWidth() + 10) + 6 + Checkbox.getBoxSize(font) + 4;
            VanillaUi.label(gfx, font, Tr.s("have_all"), x, filterY() + (Checkbox.getBoxSize(font) - 8) / 2,
                    VanillaUi.LABEL, leftX() + leftWidth() - x);
        }
        renderGrid(gfx, mouseX, mouseY);
        renderDetail(gfx, mouseX, mouseY);
    }

    private void renderGrid(GuiGraphicsExtractor gfx, int mouseX, int mouseY) {
        gridScroll = Math.min(gridScroll, maxGridScroll());
        int rowsShown = gridRows();
        for (int row = 0; row < rowsShown; row++) {
            for (int col = 0; col < columns(); col++) {
                VanillaUi.slot(gfx, gridLeft() + col * CELL, gridTop() + row * CELL);
            }
        }
        VanillaUi.scrollbar(gfx, gridLeft() + columns() * CELL + 2, gridTop(), rowsShown * CELL, gridScroll,
                maxGridScroll());
        if (filtered.isEmpty()) {
            String empty = Tr.s(state.brewableOnly ? "grid.none_brewable" : "grid.none");
            gfx.text(font, VanillaUi.fit(font, empty, columns() * CELL - 8), gridLeft() + 4, gridTop() + 5,
                    0xFFFFFFFF, true);
            return;
        }
        int hovered = cellAt(mouseX, mouseY);
        boolean potions = state.tab == Tab.POTIONS;
        for (int row = 0; row < rowsShown; row++) {
            for (int col = 0; col < columns(); col++) {
                int index = (row + gridScroll) * columns() + col;
                if (index >= filtered.size()) {
                    break;
                }
                String key = filtered.get(index);
                int x = gridLeft() + col * CELL;
                int y = gridTop() + row * CELL;
                boolean picked = key.equals(potions ? state.selected : state.selectedReagent);
                if (picked) {
                    gfx.fill(x + 1, y + 1, x + CELL - 1, y + CELL - 1, 0xFFA0A0A0);
                    gfx.outline(x, y, CELL, CELL, 0xFFFFFFFF);
                }
                if (potions && key.equals(state.tracked)) {
                    gfx.outline(x + 1, y + 1, CELL - 2, CELL - 2, 0xFFFFD84A);
                }
                ItemStack stack = potions ? data.bottle(key) : reagentStack(key);
                gfx.item(stack, x + 1, y + 1);
                boolean ready = potions ? canBrew(key) : counts.getOrDefault(key, 0) > 0;
                if (ready) {
                    gfx.fill(x + CELL - 6, y + CELL - 6, x + CELL - 2, y + CELL - 2, 0xFF1C3A12);
                    gfx.fill(x + CELL - 5, y + CELL - 5, x + CELL - 3, y + CELL - 3, 0xFF55FF55);
                }
                if (!potions && data.reagent(key).isEmpty()) {
                    gfx.text(font, "?", x + 2, y + 1, 0xFFFFFF55, true);
                }
                if (index == hovered) {
                    gfx.fill(x + 1, y + 1, x + CELL - 1, y + CELL - 1, VanillaUi.HOVER);
                }
            }
        }
        if (hovered >= 0) {
            String key = filtered.get(hovered);
            ItemStack stack = potions ? data.bottle(key) : reagentStack(key);
            gfx.setTooltipForNextFrame(font, stack, mouseX, mouseY);
        }
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
        int top = boxTop();
        boolean potions = state.tab == Tab.POTIONS;
        String key = potions ? state.selected : state.selectedReagent;
        if (key == null) {
            int room = rightEnd() - rightX() - 8;
            VanillaUi.label(gfx, font, Tr.s(potions ? "detail.potion_guide" : "detail.ingredient_guide"), x0, top + 6,
                    VanillaUi.LABEL, room);
            int y = (top + boxBottom()) / 2 - 10;
            centered(gfx, Tr.s(potions ? "detail.pick_potion" : "detail.pick_ingredient"), y, VanillaUi.LABEL);
            centered(gfx, Tr.s(potions ? "detail.dot_brew" : "detail.dot_have"), y + 12, VanillaUi.DIM);
            return;
        }
        ItemStack icon = potions ? data.bottle(key) : reagentStack(key);
        gfx.item(icon, x0, top + 4);
        VanillaUi.label(gfx, font, potions ? PotionText.full(icon) : PotionText.name(icon), x0 + 20, top + 4,
                VanillaUi.LABEL, rightEnd() - 4 - x0 - 20);
        String sub;
        if (potions) {
            Route route = data.routeFromWater(key);
            sub = route == null ? Tr.s("detail.not_from_water")
                    : Tr.s(route.steps().size() == 1 ? "detail.steps_from.one" : "detail.steps_from", route.steps().size(),
                            PotionText.name(data.bottle(route.start())));
        } else {
            sub = Tr.s("have", counts.getOrDefault(key, 0));
        }
        VanillaUi.label(gfx, font, sub, x0 + 20, top + 14, VanillaUi.DIM, rightEnd() - 4 - x0 - 20);
        gfx.fill(x0, top + 23, rightEnd() - 4, top + 24, VanillaUi.SLOT_FILL);
        if (inside(mouseX, mouseY, x0, top + 4, 16, 16)) {
            gfx.setTooltipForNextFrame(font, icon, mouseX, mouseY);
        }

        rowScroll = Math.min(rowScroll, maxRowScroll());
        ItemStack hoveredIcon = ItemStack.EMPTY;
        int y = detailTop();
        for (int i = rowScroll; i < rowScroll + rowsFitting(rowScroll) && i < rows.size(); i++) {
            Row row = rows.get(i);
            ItemStack hit = renderRow(gfx, row, x0, x1, y, mouseX, mouseY);
            if (!hit.isEmpty()) {
                hoveredIcon = hit;
            }
            y += row.height();
        }
        drawThinScrollbar(gfx, rightEnd() - 4 - THIN_SCROLLER, detailTop(), detailBottom() - detailTop(), rowScroll,
                maxRowScroll());
        if (potions) {
            renderSummary(gfx, key, x0, rightEnd() - 4);
        }
        if (!hoveredIcon.isEmpty()) {
            gfx.setTooltipForNextFrame(font, hoveredIcon, mouseX, mouseY);
        }
    }

    private void centered(GuiGraphicsExtractor gfx, String text, int y, int color) {
        int room = rightEnd() - rightX() - 8;
        String s = VanillaUi.fit(font, text, room);
        gfx.text(font, s, (rightX() + rightEnd() - font.width(s)) / 2, y, color, false);
    }

    /** Draws one detail row; returns the item under the mouse, if any. */
    private ItemStack renderRow(GuiGraphicsExtractor gfx, Row row, int x0, int x1, int y, int mouseX, int mouseY) {
        if (row.header) {
            gfx.text(font, row.text, x0, y + 3, VanillaUi.LABEL, false);
            if (!row.right.isEmpty()) {
                String right = VanillaUi.fit(font, row.right, Math.max(0, x1 - x0 - font.width(row.text) - 6));
                gfx.text(font, right, x1 - font.width(right), y + 3, VanillaUi.DIM, false);
            }
            gfx.fill(x0, y + 12, x1, y + 13, VanillaUi.SLOT_FILL);
            return ItemStack.EMPTY;
        }
        int h = row.height();
        boolean clickable = row.bottle != null || row.reagent != null;
        if (clickable && inside(mouseX, mouseY, x0 - 2, y, x1 - x0 + 4, h)) {
            gfx.fill(x0 - 2, y, x1 + 2, y + h, 0x40FFFFFF);
        }
        int x = x0;
        if (row.badge != null) {
            String badge = row.badge + ".";
            gfx.text(font, badge, x, y + 7, VanillaUi.DIM, false);
            x += font.width("9.") + 3;
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
        gfx.text(font, VanillaUi.fit(font, row.text, x1 - rightWidth - x), x, line1, row.color, false);
        if (!row.right.isEmpty()) {
            gfx.text(font, row.right, x1 - font.width(row.right), line1, row.rightColor, false);
        }
        if (twoLines) {
            gfx.text(font, VanillaUi.fit(font, row.text2, x1 - x), x, y + 12, VanillaUi.DIM, false);
            if (!row.icon2.isEmpty() && inside(mouseX, mouseY, x, y + 11, x1 - x, 10)) {
                hit = row.icon2;
            }
        }
        return hit;
    }

    /** "You carry everything" / "Missing: ...", and the stand's fuel. */
    private void renderSummary(GuiGraphicsExtractor gfx, String bottle, int x0, int x1) {
        int y = summaryTop();
        gfx.fill(x0, y - 3, x1, y - 2, VanillaUi.SLOT_FILL);
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
        String status = missing.isEmpty() ? Tr.s("status.all") : Tr.s("status.missing", String.join(", ", missing));
        VanillaUi.label(gfx, font, status, x0, y + 1, missing.isEmpty() ? VanillaUi.GOOD : VanillaUi.BAD, x1 - x0);
        y += LINE_H;
        if (parent != null) {
            int fuel = parent.getMenu().getFuel();
            int steps = route.steps().size();
            String line = fuel > 0 ? Tr.s("status.fuel", Tr.plural(fuel, "brews")) : Tr.s("status.no_fuel");
            int color = fuel < steps ? VanillaUi.WARN : VanillaUi.DIM;
            VanillaUi.label(gfx, font, line, x0, y + 1, color, x1 - x0);
        }
    }

    /** Vanilla's list scroller (the one in the world and server lists). */
    private void drawThinScrollbar(GuiGraphicsExtractor gfx, int x, int top, int height, int scroll, int maxScroll) {
        if (maxScroll <= 0) {
            return;
        }
        int thumb = Math.max(12, height * height / (height + maxScroll * ROW_H));
        int y = top + (height - thumb) * scroll / maxScroll;
        gfx.blitSprite(RenderPipelines.GUI_TEXTURED, SCROLLER_BACKGROUND, x, top, THIN_SCROLLER, height);
        gfx.blitSprite(RenderPipelines.GUI_TEXTURED, SCROLLER, x, y, THIN_SCROLLER, thumb);
    }

    private static final Identifier SCROLLER = Identifier.withDefaultNamespace("widget/scroller");
    private static final Identifier SCROLLER_BACKGROUND = Identifier.withDefaultNamespace("widget/scroller_background");

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
        if (mx >= detailLeft() - 2 && mx < detailRight() + 2 && my >= detailTop()) {
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

    @Override
    public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
        if (super.mouseScrolled(x, y, scrollX, scrollY)) {
            return true;
        }
        int delta = scrollY > 0 ? -1 : scrollY < 0 ? 1 : 0;
        if (x < rightX()) {
            gridScroll = Math.max(0, Math.min(maxGridScroll(), gridScroll + delta));
        } else {
            rowScroll = Math.max(0, Math.min(maxRowScroll(), rowScroll + delta));
        }
        return true;
    }
}
