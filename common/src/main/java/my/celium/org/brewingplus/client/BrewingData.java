package my.celium.org.brewingplus.client;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;

import my.celium.org.brewingplus.BrewGraph;
import my.celium.org.brewingplus.BrewGraph.Edge;
import my.celium.org.brewingplus.BrewGraph.Route;
import my.celium.org.brewingplus.BrewingPlusMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.component.predicates.PotionsPredicate;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.ServerPacksSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.crafting.BrewingRecipe;
import net.minecraft.world.item.crafting.PotionIngredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipePropertySet;

/**
 * The brewing recipes of the current world as a {@link BrewGraph}, plus an
 * item to draw for every bottle and reagent. Client only.
 *
 * <p>Servers never send brewing recipes to clients (only which items are
 * valid reagents), so in singleplayer and on a LAN host the recipes come
 * straight from the integrated server - datapacks included. On a remote
 * server the vanilla recipes are read from the game's own data, dropping any
 * whose reagent the server does not accept; reagents the server accepts but
 * no known recipe uses are listed as {@link #unknownReagents()}.
 */
public final class BrewingData {
    public enum Source {
        /** Exact: read from the integrated server. */
        WORLD,
        /** Vanilla recipes checked against the server's reagent list. */
        VANILLA
    }

    private static final String BREWING_TYPE = "minecraft:brewing";
    private static BrewingData current;
    private static ClientLevel currentLevel;
    /** Vanilla brewing recipe JSON, read once per game. */
    private static List<Map.Entry<String, JsonObject>> vanillaJson;

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

    private static BrewingData load(Minecraft minecraft, ClientLevel level) {
        long start = System.nanoTime();
        MinecraftServer server = minecraft.getSingleplayerServer();
        BrewingData data;
        try {
            if (server != null) {
                data = build(Source.WORLD, server.getRecipeManager().getRecipes(), null);
            } else {
                data = build(Source.VANILLA, readVanilla(level), level.recipeAccess()
                        .propertySet(RecipePropertySet.BREWING_REAGENTS));
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

    private static BrewingData build(Source source, Collection<RecipeHolder<?>> recipes, RecipePropertySet serverReagents) {
        List<Edge> edges = new ArrayList<>();
        Map<String, ItemStack> bottles = new LinkedHashMap<>();
        Map<String, ItemStack> reagents = new LinkedHashMap<>();
        List<RecipeHolder<?>> sorted = new ArrayList<>(recipes);
        sorted.sort(Comparator.comparing(holder -> holder.id().identifier().toString()));
        for (RecipeHolder<?> holder : sorted) {
            if (!(holder.value() instanceof BrewingRecipe recipe)) {
                continue;
            }
            try {
                List<String> reagentIds = new ArrayList<>();
                recipe.getReagent().ingredient().items().forEach(item -> {
                    ItemStack stack = new ItemStack(item);
                    if (serverReagents == null || serverReagents.test(stack)) {
                        reagentIds.add(item.getRegisteredName());
                        reagents.putIfAbsent(item.getRegisteredName(), stack);
                    }
                });
                if (reagentIds.isEmpty()) {
                    continue;
                }
                ItemStack output = recipe.getOutput().create();
                String outKey = keyOf(output);
                bottles.putIfAbsent(outKey, output);
                String recipeId = holder.id().identifier().toString();
                for (String inKey : inputKeys(recipe.getInput(), bottles)) {
                    edges.add(new Edge(inKey, reagentIds, outKey, recipeId));
                }
            } catch (RuntimeException e) {
                BrewingPlusMod.debug("Skipped brewing recipe {}: {}", holder.id(), e.toString());
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
    private static List<String> inputKeys(PotionIngredient input, Map<String, ItemStack> bottles) {
        List<Holder<Potion>> potions = input.potions()
                .flatMap(PotionsPredicate::potions)
                .map(HolderSet::stream)
                .map(stream -> stream.toList())
                .orElse(List.of());
        List<String> keys = new ArrayList<>();
        input.ingredient().items().forEach(itemHolder -> {
            Item item = itemHolder.value();
            String itemId = itemHolder.getRegisteredName();
            if (potions.isEmpty()) {
                String key = BrewGraph.key(itemId, BrewGraph.ANY);
                bottles.putIfAbsent(key, new ItemStack(item));
                keys.add(key);
                return;
            }
            for (Holder<Potion> potion : potions) {
                String key = BrewGraph.key(itemId, potion.getRegisteredName());
                bottles.putIfAbsent(key, PotionContents.createItemStack(item, potion));
                keys.add(key);
            }
        });
        return keys;
    }

    /** Decodes the vanilla brewing recipes against the level's registries. */
    private static List<RecipeHolder<?>> readVanilla(ClientLevel level) {
        RegistryOps<JsonElement> ops = level.registryAccess().createSerializationContext(JsonOps.INSTANCE);
        List<RecipeHolder<?>> out = new ArrayList<>();
        for (Map.Entry<String, JsonObject> entry : vanillaJson()) {
            Optional<Recipe<?>> recipe = Recipe.DIRECT_CODEC.parse(ops, entry.getValue()).result();
            if (recipe.isPresent()) {
                out.add(new RecipeHolder<>(ResourceKey.create(Registries.RECIPE, Identifier.parse(entry.getKey())),
                        recipe.get()));
            } else {
                BrewingPlusMod.debug("Could not decode vanilla recipe {}", entry.getKey());
            }
        }
        return out;
    }

    private static synchronized List<Map.Entry<String, JsonObject>> vanillaJson() {
        if (vanillaJson != null) {
            return vanillaJson;
        }
        Map<String, JsonObject> found = new HashMap<>();
        try (PackResources pack = ServerPacksSource.createVanillaPackSource().fullResources()) {
            pack.listResources(PackType.SERVER_DATA, "minecraft", "recipe", (id, supplier) -> {
                try (InputStream in = supplier.get();
                        Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                    JsonElement json = JsonParser.parseReader(reader);
                    if (json.isJsonObject() && json.getAsJsonObject().has("type")
                            && BREWING_TYPE.equals(json.getAsJsonObject().get("type").getAsString())) {
                        String path = id.getPath();
                        String name = path.substring("recipe/".length(), path.length() - ".json".length());
                        found.put(id.getNamespace() + ":" + name, json.getAsJsonObject());
                    }
                } catch (Exception e) {
                    BrewingPlusMod.debug("Could not read {}: {}", id, e.toString());
                }
            });
        } catch (Exception e) {
            BrewingPlusMod.debug("Could not open the vanilla data pack: {}", e.toString());
        }
        List<Map.Entry<String, JsonObject>> sorted = new ArrayList<>(found.entrySet());
        sorted.sort(Map.Entry.comparingByKey());
        vanillaJson = List.copyOf(sorted);
        return vanillaJson;
    }
}
