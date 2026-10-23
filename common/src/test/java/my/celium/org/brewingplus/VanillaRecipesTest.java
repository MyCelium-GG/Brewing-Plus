package my.celium.org.brewingplus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.Reader;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import my.celium.org.brewingplus.BrewGraph.Edge;
import my.celium.org.brewingplus.BrewGraph.Route;

/**
 * Runs the graph over the real vanilla 26.3 brewing recipes (read as plain
 * JSON from the game jar, no bootstrap): every potion is reachable from water
 * and the classic chains come out the way players brew them.
 */
class VanillaRecipesTest {
    static final String MARKER = "data/minecraft/recipe/brewing/potion_water_nether_wart.json";
    static BrewGraph graph;
    static Map<String, Route> routes;

    @BeforeAll
    static void load() throws IOException {
        URL marker = VanillaRecipesTest.class.getClassLoader().getResource(MARKER);
        assertNotNull(marker, "vanilla brewing recipes on the test classpath");
        List<Edge> edges = new ArrayList<>();
        URI uri = URI.create(marker.toString());
        if ("jar".equals(uri.getScheme())) {
            try (FileSystem fs = FileSystems.newFileSystem(uri, Map.of())) {
                read(fs.getPath(MARKER).getParent(), edges);
            }
        } else {
            read(Path.of(uri).getParent(), edges);
        }
        graph = new BrewGraph(edges);
        routes = graph.routes(graph.defaultStarts());
    }

    private static void read(Path dir, List<Edge> edges) throws IOException {
        try (Stream<Path> files = Files.list(dir)) {
            for (Path file : files.sorted().toList()) {
                try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                    JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
                    JsonObject input = json.getAsJsonObject("input");
                    String from = BrewGraph.key(input.get("item").getAsString(),
                            input.getAsJsonObject("potion_contents").get("potions").getAsString());
                    JsonObject output = json.getAsJsonObject("output");
                    String to = BrewGraph.key(output.get("id").getAsString(), output.getAsJsonObject("components")
                            .getAsJsonObject("minecraft:potion_contents").get("potion").getAsString());
                    String reagent = json.getAsJsonObject("reagent").get("item").getAsString();
                    edges.add(new Edge(from, List.of(reagent), to, file.getFileName().toString()));
                }
            }
        }
    }

    static List<String> reagents(String item, String potion) {
        Route route = routes.get(BrewGraph.key(item, potion));
        assertNotNull(route, potion + " reachable");
        return route.steps().stream().map(step -> step.reagents().get(0).replace("minecraft:", "")).toList();
    }

    @Test
    void everyBottleIsReachableFromWater() {
        assertTrue(graph.edges().size() > 250, "all vanilla brewing recipes read");
        assertEquals(List.of(BrewGraph.WATER), graph.roots());
        for (String bottle : graph.bottles()) {
            assertNotNull(routes.get(bottle), bottle + " reachable from water");
        }
    }

    @Test
    void classicChains() {
        assertEquals(List.of("nether_wart", "golden_carrot"), reagents("minecraft:potion", "minecraft:night_vision"));
        assertEquals(List.of("nether_wart", "golden_carrot", "fermented_spider_eye"),
                reagents("minecraft:potion", "minecraft:invisibility"));
        assertEquals(List.of("nether_wart", "glistering_melon_slice", "glowstone_dust"),
                reagents("minecraft:potion", "minecraft:strong_healing"));
        assertEquals(List.of("fermented_spider_eye"), reagents("minecraft:potion", "minecraft:weakness"));
    }

    @Test
    void throwableFormsComeLast() {
        assertEquals(List.of("nether_wart", "golden_carrot", "redstone", "gunpowder"),
                reagents("minecraft:splash_potion", "minecraft:long_night_vision"));
        assertEquals(List.of("nether_wart", "golden_carrot", "redstone", "gunpowder", "dragon_breath"),
                reagents("minecraft:lingering_potion", "minecraft:long_night_vision"));
    }
}
