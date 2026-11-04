package my.celium.org.brewingplus;

import my.celium.org.stats.ModStats;

/**
 * Anonymous stats for the FastStats project {@code brewing}, sent through Mycel under its
 * consent: how often the guide is opened, which potions and ingredients get looked up and tracked, and the settings.
 */
public final class BrewingStats {
    private static final String TOKEN = "f1a3017af05c61b33e1de3b26bd99485";

    private static volatile ModStats stats;

    private BrewingStats() {
    }

    static void start() {
        stats = ModStats.builder(BrewingPlusMod.META, TOKEN)
                .bool("enabled", () -> BrewingPlusMod.enabled.get())
                .bool("show_button", () -> BrewingPlusMod.showButton.get())
                .bool("show_stand_hint", () -> BrewingPlusMod.showStandHint.get())
                .counters("looked_up_potions")
                .counters("looked_up_ingredients")
                .counters("tracked_potions")
                .errors("my.celium.org.brewingplus.")
                .start();
    }

    /** Counts a feature use (sent as {@code usage}). */
    public static void count(String what) {
        ModStats current = stats;
        if (current != null) {
            current.count(what);
        }
    }

    /** Counts {@code key} in the counter map {@code id}. */
    public static void count(String id, String key) {
        ModStats current = stats;
        if (current != null && key != null) {
            current.count(id, key.startsWith("minecraft:") ? key.substring(10) : key);
        }
    }
}
