package my.celium.org.brewingplus;

import my.celium.org.brewingplus.stand.ReadyPacket;
import my.celium.org.brewingplus.stand.StandPacket;
import my.celium.org.brewingplus.stand.StandServer;
import my.celium.org.event.MycelEvents;
import my.celium.org.network.MycelNetwork;
import my.celium.org.network.PacketDefinition;

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
 * <p>Mostly client side. {@link #init()} registers metadata, config and the stand packets with
 * Mycel; each loader's <em>client</em> entrypoint then adds the button, the stand hint and the
 * look-at card. On a server (or in singleplayer) {@code StandServer} sends nearby stands and tells
 * players when their brews finish. Dedicated servers never touch client classes.
 */
public final class BrewingPlusMod {
    public static final String ID = "brewingplus";

    public static final ModMetadata META = ModMetadata.builder(ID, "Brewing+", "1.2.0")
            .author("MyCelium")
            .website("https://www.curseforge.com/minecraft/mc-mods/brewing-plus")
            .updateUrl("https://wiki.mycelium-gg.org/updates/brewingplus.json")
            .build();

    public static ConfigValue<Boolean> enabled;
    public static ConfigValue<Boolean> showButton;
    public static ConfigValue<Boolean> showStandHint;
    public static ConfigValue<Boolean> readyMessage;
    public static ConfigValue<Boolean> standCard;
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
        readyMessage = builder.booleanValue("readyMessage", true,
                "Tell you in chat when a brew you started finishes, with the next step of the tracked potion. "
                        + "Needs Brewing+ on the server too (always there in singleplayer).");
        standCard = builder.booleanValue("standCard", true,
                "Look at a brewing stand to see its bottles, time left and fuel. Needs Brewing+ on the server too.");
        debug = builder.booleanValue("debug", false,
                "Log recipe loading, guide clicks and routes to the game log.");
        config = builder.build();
        BrewingStats.start();

        // Optional both ways: clients without the mod get a plain chat line, servers without it send nothing.
        StandServer.stands = PacketDefinition.toClient(StandPacket.TYPE, StandPacket.CODEC,
                (packet, context) -> my.celium.org.brewingplus.client.StandClient.receive(packet));
        StandServer.ready = PacketDefinition.toClient(ReadyPacket.TYPE, ReadyPacket.CODEC,
                (packet, context) -> my.celium.org.brewingplus.client.StandClient.ready(packet));
        MycelNetwork.register(META, StandServer.stands);
        MycelNetwork.register(META, StandServer.ready);
        MycelEvents.onServerLifecycle(event -> StandServer.clear());
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
