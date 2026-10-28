package my.celium.org.brewingplus.client;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

import my.celium.org.brewingplus.BrewingPlusMod;
import my.celium.org.brewingplus.client.BrewingData.Brew;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.Holder;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.PotionBrewing;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.crafting.Ingredient;

/**
 * Where {@link BrewingData} gets its recipes. Before 26.3 brewing is built in
 * code ({@link PotionBrewing}: potion mixes and bottle mixes), on the client
 * as well as on the server; this is the 26.2 copy (one per line in
 * common/src/mc). The mixes are expanded into one recipe per bottle and
 * potion, the way 26.3 ships them as data.
 */
final class BrewingRecipes {
    private BrewingRecipes() {
    }

    /** The integrated server's brewing. */
    static List<Brew> world(MinecraftServer server) {
        return brews(server.potionBrewing());
    }

    /** The client's own brewing: vanilla for the server's enabled features. */
    static List<Brew> vanilla(ClientLevel level) {
        return brews(level.potionBrewing());
    }

    /** Servers send no reagent list before 26.3; every recipe is trusted. */
    static Predicate<ItemStack> serverReagents(ClientLevel level) {
        return null;
    }

    private static List<Brew> brews(PotionBrewing brewing) {
        List<Brew> out = new ArrayList<>();
        try {
            List<Holder<Item>> containers = new ArrayList<>();
            for (Ingredient container : BrewingRecipes.<Ingredient>list(brewing, "containers")) {
                container.items().forEach(containers::add);
            }
            Set<Holder<Potion>> potions = new LinkedHashSet<>();
            for (Object mix : list(brewing, "potionMixes")) {
                Holder<Potion> from = part(mix, "from");
                Holder<Potion> to = part(mix, "to");
                Ingredient reagent = part(mix, "ingredient");
                potions.add(from);
                potions.add(to);
                for (Holder<Item> container : containers) {
                    out.add(new Brew(id(container, from, reagent), List.of(container), List.of(from),
                            reagent.items().toList(), PotionContents.createItemStack(container.value(), to)));
                }
            }
            for (Object mix : list(brewing, "containerMixes")) {
                Holder<Item> from = part(mix, "from");
                Holder<Item> to = part(mix, "to");
                Ingredient reagent = part(mix, "ingredient");
                for (Holder<Potion> potion : potions) {
                    out.add(new Brew(id(from, potion, reagent), List.of(from), List.of(potion),
                            reagent.items().toList(), PotionContents.createItemStack(to.value(), potion)));
                }
            }
        } catch (ReflectiveOperationException | RuntimeException e) {
            BrewingPlusMod.debug("Could not read the brewing mixes: {}", e.toString());
        }
        return out;
    }

    /** Named like 26.3's recipe files: {@code minecraft:brewing/<bottle>_<potion>_<reagent>}. */
    private static String id(Holder<Item> bottle, Holder<Potion> potion, Ingredient reagent) {
        String reagentName = reagent.items().findFirst().map(BrewingRecipes::path).orElse("unknown");
        return "minecraft:brewing/" + path(bottle) + "_" + path(potion) + "_" + reagentName;
    }

    private static String path(Holder<?> holder) {
        String name = holder.getRegisteredName();
        return name.substring(name.indexOf(':') + 1);
    }

    // PotionBrewing keeps its mixes private; this runs once per world.
    @SuppressWarnings("unchecked")
    private static <T> List<T> list(PotionBrewing brewing, String name) throws ReflectiveOperationException {
        Field field = PotionBrewing.class.getDeclaredField(name);
        field.setAccessible(true);
        return (List<T>) field.get(brewing);
    }

    @SuppressWarnings("unchecked")
    private static <T> T part(Object mix, String name) throws ReflectiveOperationException {
        Method accessor = mix.getClass().getDeclaredMethod(name);
        accessor.setAccessible(true);
        return (T) accessor.invoke(mix);
    }
}
