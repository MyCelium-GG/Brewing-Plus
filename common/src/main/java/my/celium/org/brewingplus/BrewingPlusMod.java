package my.celium.org.brewingplus;

import my.celium.org.Mycel;
import my.celium.org.config.ConfigBuilder;
import my.celium.org.config.ConfigValue;
import my.celium.org.config.MycelConfig;
import my.celium.org.logging.MycelLog;
import my.celium.org.metadata.ModMetadata;

/**
 * Brewing+: a button on the brewing stand opens a guide to every potion -
 * its full recipe chain, which ingredients you carry, what each ingredient
 * does - and a tracked potion puts a hint in the stand showing the next
 * ingredient to add.
 *
 * <p>Client-side only. {@link #init()} registers metadata and config with
 * Mycel; each loader's <em>client</em> entrypoint then adds the button and
 * the stand hint. Dedicated servers never touch client classes.
 */
public final class BrewingPlusMod {
    public static final String ID = "brewingplus";

    public static final ModMetadata META = ModMetadata.builder(ID, "Brewing+", "1.0.2")
            .author("MyCelium")
            .website("https://www.curseforge.com/minecraft/mc-mods/brewing-plus")
            .updateUrl("https://wiki.mycelium-gg.org/updates/brewingplus.json")
            .build();

    public static ConfigValue<Boolean> enabled;
    public static ConfigValue<Boolean> showButton;
    public static ConfigValue<Boolean> showStandHint;
    public static ConfigValue<Boolean> debug;
    public static MycelConfig config;

    private BrewingPlusMod() {
    }

    /** Called once from each loader's entrypoint. */
    public static void init() {
        Mycel.initialize();
        Mycel.registerMod(META);

        ConfigBuilder builder = Mycel.config(META);
        enabled = builder.booleanValue("enabled", true,
                "Whether the mod does anything at all.");
        showButton = builder.booleanValue("showButton", true,
                "Show the guide button on the brewing stand screen.");
        showStandHint = builder.booleanValue("showStandHint", true,
                "While a potion is tracked, show the next ingredient in the brewing stand.");
        debug = builder.booleanValue("debug", false,
                "Log recipe loading, guide clicks and routes to the game log.");
        config = builder.build();
    }

    /** Info-level log line tagged {@code [debug]}, only while the {@code debug} option is on. */
    public static void debug(String message, Object... args) {
        try {
            if (debug == null || !debug.get()) {
                return;
            }
            MycelLog.infoMod(ID, "[debug] " + message, args);
        } catch (RuntimeException e) {
            // Logging must never break the caller.
        }
    }

    public static boolean isModEnabled() {
        return Mycel.isEnabled(ID) && enabled.get();
    }
}
