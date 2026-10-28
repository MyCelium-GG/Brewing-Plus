package my.celium.org.brewingplus.client;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

import my.celium.org.brewingplus.BrewGraph;
import my.celium.org.brewingplus.BrewGraph.Edge;
import my.celium.org.brewingplus.BrewGraph.Route;
import my.celium.org.brewingplus.BrewingPlusMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.PotionContents;

/**
 * The brewing recipes of the current world as a {@link BrewGraph}, plus an
 * item to draw for every bottle and reagent. Client only.
 *
 * <p>Servers never send brewing recipes to clients (only which items are
 * valid reagents), so in singleplayer and on a LAN host the recipes come
 * straight from the integrated server - datapacks included. On a remote
 * server the vanilla recipes are read from the game's own data, dropping any
 * whose reagent the server does not accept; reagents the server accepts but
 * no known recipe uses are listed as {@link #unknownReagents()}. Before 26.3
 * brewing is built in code on both sides, so a remote server's recipes are the
 * client's own (see {@code BrewingRecipes}, one per Minecraft line).
 */
public final class BrewingData {
    public enum Source {
        /** Exact: read from the integrated server. */
        WORLD,
        /** Vanilla recipes checked against the server's reagent list. */
        VANILLA
    }

    private static BrewingData current;
    private static ClientLevel currentLevel;

    private final Source source;
    private final BrewGraph graph;
    private final Map<String, ItemStack> bottleStacks;
    private final Map<String, ItemStack> reagentStacks;
    private final List<ItemStack> unknownReagents;
    private final Map<String, Route> fromWater;

    private BrewingData(Source source, BrewGraph graph, Map<String, ItemStack> bottleStacks,
            Map<String, ItemStack> reagentStacks, List<ItemStack> unknownReagents) {
        this.source = source;
        this.graph = graph;
        this.bottleStacks = bottleStacks;
        this.reagentStacks = reagentStacks;
        this.unknownReagents = unknownReagents;
        this.fromWater = graph.routes(graph.defaultStarts());
    }

    /** Recipes for the current level, built on first use and after every world change. */
    public static synchronized BrewingData get() {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null) {
            return null;
        }
        if (current == null || currentLevel != level) {
            current = load(minecraft, level);
            currentLevel = level;
        }
        return current;
    }

    /** Forgets the cached recipes so the next {@link #get()} rereads them (e.g. after /reload). */
    public static synchronized void invalidate() {
        current = null;
    }

    public Source source() {
        return source;
    }

    public BrewGraph graph() {
        return graph;
    }

    /** Cheapest route from a water bottle (or another root) to {@code bottle}, or {@code null}. */
    public Route routeFromWater(String bottle) {
        return fromWater.get(bottle);
    }

    /** Every bottle some recipe makes, in recipe order. */
    public List<String> brewable() {
        List<String> out = new ArrayList<>();
        for (String bottle : graph.bottles()) {
            if (!graph.edgesInto(bottle).isEmpty()) {
                out.add(bottle);
            }
        }
        return out;
    }

    public ItemStack bottle(String key) {
        return bottleStacks.getOrDefault(key, ItemStack.EMPTY);
    }

    public ItemStack reagent(String id) {
        return reagentStacks.getOrDefault(id, ItemStack.EMPTY);
    }

    /** Items the server accepts as reagents that no known recipe uses (remote servers only). */
    public List<ItemStack> unknownReagents() {
        return unknownReagents;
    }

    /** Graph key of a real bottle: {@code item|potion}, or {@code item|} without potion contents. */
    public static String keyOf(ItemStack stack) {
        String item = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        PotionContents contents = stack.get(DataComponents.POTION_CONTENTS);
        String potion = contents == null ? "" : contents.potion().map(Holder::getRegisteredName).orElse("");
        return BrewGraph.key(item, potion);
    }

    public static String idOf(Item item) {
        return BuiltInRegistries.ITEM.getKey(item).toString();
    }

    // ------------------------------------------------------------- loading

    /**
     * One brewing recipe, whatever its source: every bottle item in {@code inputItems}
     * holding one of {@code inputPotions} (none = any potion) plus one of
     * {@code reagents} gives {@code output}. Made by {@code BrewingRecipes}, which
     * differs between Minecraft lines (data-driven recipes since 26.3).
     */
    record Brew(String id, List<Holder<Item>> inputItems, List<Holder<Potion>> inputPotions,
            List<Holder<Item>> reagents, ItemStack output) {
    }

    private static BrewingData load(Minecraft minecraft, ClientLevel level) {
        long start = System.nanoTime();
        MinecraftServer server = minecraft.getSingleplayerServer();
        BrewingData data;
        try {
            if (server != null) {
                data = build(Source.WORLD, BrewingRecipes.world(server), null);
            } else {
                data = build(Source.VANILLA, BrewingRecipes.vanilla(level), BrewingRecipes.serverReagents(level));
            }
        } catch (RuntimeException e) {
            BrewingPlusMod.debug("Recipe loading failed: {}", e.toString());
            data = build(Source.VANILLA, List.of(), null);
        }
        BrewingPlusMod.debug("Loaded {} brewing recipes ({}) into {} bottles, {} reagents, {} unknown reagents in {} ms",
                data.graph.edges().size(), data.source, data.graph.bottles().size(), data.graph.reagents().size(),
                data.unknownReagents.size(), (System.nanoTime() - start) / 1_000_000);
        return data;
    }

    /** {@code serverReagents}: what a remote server accepts as a reagent, or {@code null} to trust every recipe. */
    private static BrewingData build(Source source, List<Brew> recipes, Predicate<ItemStack> serverReagents) {
        List<Edge> edges = new ArrayList<>();
        Map<String, ItemStack> bottles = new LinkedHashMap<>();
        Map<String, ItemStack> reagents = new LinkedHashMap<>();
        List<Brew> sorted = new ArrayList<>(recipes);
        sorted.sort(Comparator.comparing(Brew::id));
        for (Brew recipe : sorted) {
            try {
                List<String> reagentIds = new ArrayList<>();
                for (Holder<Item> item : recipe.reagents()) {
                    ItemStack stack = new ItemStack(item);
                    if (serverReagents == null || serverReagents.test(stack)) {
                        reagentIds.add(item.getRegisteredName());
                        reagents.putIfAbsent(item.getRegisteredName(), stack);
                    }
                }
                if (reagentIds.isEmpty()) {
                    continue;
                }
                ItemStack output = recipe.output();
                String outKey = keyOf(output);
                bottles.putIfAbsent(outKey, output);
                for (String inKey : inputKeys(recipe, bottles)) {
                    edges.add(new Edge(inKey, reagentIds, outKey, recipe.id()));
                }
            } catch (RuntimeException e) {
                BrewingPlusMod.debug("Skipped brewing recipe {}: {}", recipe.id(), e.toString());
            }
        }
        List<ItemStack> unknown = new ArrayList<>();
        if (serverReagents != null) {
            for (Item item : BuiltInRegistries.ITEM) {
                ItemStack stack = new ItemStack(item);
                if (!stack.isEmpty() && !reagents.containsKey(idOf(item)) && serverReagents.test(stack)) {
                    unknown.add(stack);
                }
            }
        }
        return new BrewingData(source, new BrewGraph(edges), bottles, reagents, List.copyOf(unknown));
    }

    /** One key per accepted item and potion; recipes without a potion filter accept any potion. */
    private static List<String> inputKeys(Brew recipe, Map<String, ItemStack> bottles) {
        List<String> keys = new ArrayList<>();
        for (Holder<Item> itemHolder : recipe.inputItems()) {
            Item item = itemHolder.value();
            String itemId = itemHolder.getRegisteredName();
            if (recipe.inputPotions().isEmpty()) {
                String key = BrewGraph.key(itemId, BrewGraph.ANY);
                bottles.putIfAbsent(key, new ItemStack(item));
                keys.add(key);
                continue;
            }
            for (Holder<Potion> potion : recipe.inputPotions()) {
                String key = BrewGraph.key(itemId, potion.getRegisteredName());
                bottles.putIfAbsent(key, PotionContents.createItemStack(item, potion));
                keys.add(key);
            }
        }
        return keys;
    }
}
