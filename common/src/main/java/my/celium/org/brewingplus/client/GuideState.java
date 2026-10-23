package my.celium.org.brewingplus.client;

import my.celium.org.brewingplus.BrewGraph;

/**
 * What the guide shows, kept for the whole session so reopening it (or
 * coming back from the stand) restores the last page. Bottles are
 * {@link BrewGraph} keys, reagents item ids.
 */
public final class GuideState {
    enum Tab {
        POTIONS,
        INGREDIENTS
    }

    /** Bottle filter on the potion grid. */
    enum Form {
        ALL("All", null),
        DRINK("Potion", "minecraft:potion"),
        SPLASH("Splash", "minecraft:splash_potion"),
        LINGERING("Lingering", "minecraft:lingering_potion");

        final String label;
        final String item;

        Form(String label, String item) {
            this.label = label;
            this.item = item;
        }

        boolean accepts(String bottle) {
            return item == null || item.equals(BrewGraph.itemOf(bottle));
        }
    }

    Tab tab = Tab.POTIONS;
    Form form = Form.ALL;
    boolean brewableOnly;
    String search = "";
    /** The potion shown on the potions page, or {@code null}. */
    String selected;
    /** The ingredient shown on the ingredients page, or {@code null}. */
    String selectedReagent;
    /** The potion the stand hint works towards, or {@code null}. */
    String tracked;

    static final GuideState CURRENT = new GuideState();

    /** The bottle the stand hint is tracking, or {@code null}. */
    public static String tracked() {
        return CURRENT.tracked;
    }
}
