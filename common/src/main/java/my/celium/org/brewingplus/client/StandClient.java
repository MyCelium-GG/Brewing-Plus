package my.celium.org.brewingplus.client;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import my.celium.org.brewingplus.BrewGraph;
import my.celium.org.brewingplus.BrewGraph.Edge;
import my.celium.org.brewingplus.BrewGraph.Route;
import my.celium.org.brewingplus.BrewingPlusMod;
import my.celium.org.brewingplus.stand.ReadyPacket;
import my.celium.org.brewingplus.stand.StandInfo;
import my.celium.org.brewingplus.stand.StandPacket;
import my.celium.org.util.ChatVoice;
import my.celium.org.util.TextUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;

/** The server's brewing stand data on the client, and the "your potions are ready" message. */
public final class StandClient {
    static final long FRESH = 60;
    private static final ChatVoice VOICE = TextUtil.voice("Brewing+");
    private static final Map<BlockPos, Entry> STANDS = new ConcurrentHashMap<>();

    private record Entry(StandInfo info, long receivedAt) {
    }

    private StandClient() {
    }

    public static void receive(StandPacket packet) {
        long now = now();
        for (StandInfo info : packet.stands()) {
            STANDS.put(info.pos(), new Entry(info, now));
        }
    }

    /** A stand's data aged to now (its brew time counts down), or {@code null}. */
    static StandInfo get(BlockPos pos) {
        Entry entry = pos == null ? null : STANDS.get(pos);
        if (entry == null) {
            return null;
        }
        long age = now() - entry.receivedAt;
        if (age < 0 || age > FRESH) {
            STANDS.remove(pos);
            return null;
        }
        StandInfo i = entry.info;
        return new StandInfo(i.pos(), (int) Math.max(0, i.brewTime() - age), i.fuelBrews(), i.bottles(), i.ingredient());
    }

    public static void clear() {
        STANDS.clear();
    }

    /** A brew the player started has finished somewhere. */
    public static void ready(ReadyPacket packet) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || !BrewingPlusMod.isModEnabled()) {
            return;
        }
        StandInfo stand = packet.stand();
        List<String> filled = stand.filled();
        if (filled.isEmpty()) {
            return;
        }
        BrewingData data = BrewingData.get();
        MutableComponent line = VOICE.note("Ready: " + counts(data, filled),
                TextUtil.muted("   " + where(minecraft, stand.pos())));
        minecraft.player.sendSystemMessage(line);
        String next = nextStep(data, filled.get(0), "");
        if (next != null) {
            minecraft.player.sendSystemMessage(VOICE.note(next));
        }
        try {
            minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_CHIME, 1.6F));
        } catch (RuntimeException e) {
            // Sound is optional.
        }
    }

    /** "3 x Awkward Potion" or "2 x Awkward Potion, 1 x Water Bottle". */
    static String counts(BrewingData data, List<String> bottles) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (String bottle : bottles) {
            counts.merge(bottle, 1, Integer::sum);
        }
        List<String> parts = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : counts.entrySet()) {
            parts.add(entry.getValue() + " x " + name(data, entry.getKey()));
        }
        return String.join(", ", parts);
    }

    static String name(BrewingData data, String key) {
        if (data != null) {
            ItemStack stack = data.bottle(key);
            if (stack != null && !stack.isEmpty()) {
                return PotionText.name(stack);
            }
        }
        String potion = BrewGraph.potionOf(key);
        return potion.isEmpty() ? BrewGraph.itemOf(key) : potion.substring(potion.indexOf(':') + 1).replace('_', ' ');
    }

    static String itemName(String id) {
        try {
            return new ItemStack(BuiltInRegistries.ITEM.getValue(Identifier.parse(id))).getHoverName().getString();
        } catch (RuntimeException e) {
            return id;
        }
    }

    /**
     * The tracked potion's next step from these bottles: "Next for Potion of Swiftness: Sugar (last step)",
     * "This brew makes your Potion of Swiftness", or {@code null} when nothing is tracked or no route exists.
     *
     * @param brewing the ingredient brewing right now ("" when idle): that step is already under way
     */
    static String nextStep(BrewingData data, String bottle, String brewing) {
        String tracked = GuideState.tracked();
        if (tracked == null || data == null) {
            return null;
        }
        String target = name(data, tracked);
        if (tracked.equals(bottle)) {
            return "Your " + target + " is done";
        }
        Route route = data.graph().route(bottle, tracked);
        if (route == null || route.steps().isEmpty()) {
            return null;
        }
        List<Edge> steps = route.steps();
        if (!brewing.isEmpty() && steps.get(0).reagents().contains(brewing)) {
            steps = steps.subList(1, steps.size());
            if (steps.isEmpty()) {
                return "This brew makes your " + target;
            }
        }
        String more = steps.size() > 1 ? " (" + steps.size() + " steps left)" : " (last step)";
        return "Next for " + target + ": " + PotionText.name(data.reagent(steps.get(0).reagents().get(0))) + more;
    }

    /** "12 blocks north". */
    static String where(Minecraft minecraft, BlockPos pos) {
        double dx = pos.getX() + 0.5 - minecraft.player.getX();
        double dz = pos.getZ() + 0.5 - minecraft.player.getZ();
        int distance = (int) Math.round(Math.sqrt(dx * dx + dz * dz));
        if (distance < 2) {
            return "right here";
        }
        String dir = Math.abs(dx) > Math.abs(dz) * 2 ? (dx > 0 ? "east" : "west")
                : Math.abs(dz) > Math.abs(dx) * 2 ? (dz > 0 ? "south" : "north")
                : (dz > 0 ? "south" : "north") + (dx > 0 ? "east" : "west");
        return distance + " blocks " + dir;
    }

    private static long now() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.level == null ? 0 : minecraft.level.getGameTime();
    }
}
