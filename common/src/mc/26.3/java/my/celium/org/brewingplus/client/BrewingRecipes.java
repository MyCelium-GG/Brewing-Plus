package my.celium.org.brewingplus.client;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;

import my.celium.org.brewingplus.BrewingPlusMod;
import my.celium.org.brewingplus.client.BrewingData.Brew;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.component.predicates.PotionsPredicate;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.ServerPacksSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.crafting.BrewingRecipe;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipePropertySet;

/**
 * Where {@link BrewingData} gets its recipes. Since 26.3 brewing recipes are
 * data-driven ({@code minecraft:brewing}); this is the 26.3 copy (one per line
 * in common/src/mc).
 */
final class BrewingRecipes {
    private static final String BREWING_TYPE = "minecraft:brewing";
    /** Vanilla brewing recipe JSON, read once per game. */
    private static List<Map.Entry<String, JsonObject>> vanillaJson;

    private BrewingRecipes() {
    }

    /** The integrated server's recipes, datapacks included. */
    static List<Brew> world(MinecraftServer server) {
        return brews(server.getRecipeManager().getRecipes());
    }

    /** The vanilla recipes, decoded against the level's registries. */
    static List<Brew> vanilla(ClientLevel level) {
        return brews(readVanilla(level));
    }

    /** The reagents a remote server accepts (servers send that list, not the recipes). */
    static Predicate<ItemStack> serverReagents(ClientLevel level) {
        return level.recipeAccess().propertySet(RecipePropertySet.BREWING_REAGENTS)::test;
    }

    private static List<Brew> brews(Collection<RecipeHolder<?>> recipes) {
        List<Brew> out = new ArrayList<>();
        for (RecipeHolder<?> holder : recipes) {
            if (!(holder.value() instanceof BrewingRecipe recipe)) {
                continue;
            }
            try {
                List<Holder<Potion>> potions = recipe.getInput().potions()
                        .flatMap(PotionsPredicate::potions)
                        .map(HolderSet::stream)
                        .map(stream -> stream.toList())
                        .orElse(List.of());
                out.add(new Brew(holder.id().identifier().toString(), recipe.getInput().ingredient().items().toList(),
                        potions, recipe.getReagent().ingredient().items().toList(), recipe.getOutput().create()));
            } catch (RuntimeException e) {
                BrewingPlusMod.debug("Skipped brewing recipe {}: {}", holder.id(), e.toString());
            }
        }
        return out;
    }

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
