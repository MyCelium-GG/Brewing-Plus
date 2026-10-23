package my.celium.org.brewingplus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import my.celium.org.brewingplus.BrewGraph.Edge;
import my.celium.org.brewingplus.BrewGraph.Route;

/** Headless tests for routing, reagent picking and "any potion" recipes. */
class BrewGraphTest {
    static final String POTION = "minecraft:potion";
    static final String SPLASH = "minecraft:splash_potion";

    static String p(String potion) {
        return BrewGraph.key(POTION, "minecraft:" + potion);
    }

    static String s(String potion) {
        return BrewGraph.key(SPLASH, "minecraft:" + potion);
    }

    static Edge e(String from, String to, String... reagents) {
        return new Edge(from, Arrays.stream(reagents).map(r -> "minecraft:" + r).toList(), to, "test:" + to);
    }

    /** Night vision, invisibility and their splash forms, with both splash orders available. */
    static final BrewGraph GRAPH = new BrewGraph(List.of(
            e(p("water"), p("awkward"), "nether_wart"),
            e(p("awkward"), p("night_vision"), "golden_carrot"),
            e(p("night_vision"), p("invisibility"), "fermented_spider_eye"),
            e(p("night_vision"), p("long_night_vision"), "redstone"),
            e(p("water"), s("water"), "gunpowder"),
            e(p("awkward"), s("awkward"), "gunpowder"),
            e(s("awkward"), s("night_vision"), "golden_carrot"),
            e(p("night_vision"), s("night_vision"), "gunpowder"),
            e(p("invisibility"), s("invisibility"), "gunpowder")));

    static List<String> reagents(Route route) {
        return route.steps().stream().map(step -> step.reagents().get(0)).toList();
    }

    @Test
    void keysRoundTrip() {
        String key = BrewGraph.key(SPLASH, "minecraft:night_vision");
        assertEquals(SPLASH, BrewGraph.itemOf(key));
        assertEquals("minecraft:night_vision", BrewGraph.potionOf(key));
        assertFalse(BrewGraph.isAny(key));
        assertTrue(BrewGraph.isAny(BrewGraph.key(POTION, BrewGraph.ANY)));
    }

    @Test
    void routeFromWater() {
        Route route = GRAPH.route(BrewGraph.WATER, p("invisibility"));
        assertEquals(List.of("minecraft:nether_wart", "minecraft:golden_carrot", "minecraft:fermented_spider_eye"),
                reagents(route));
        assertEquals(BrewGraph.WATER, route.start());
        assertEquals(p("invisibility"), route.target());
    }

    @Test
    void splashConversionComesLast() {
        // Both orders take three steps; brewing as a drinkable potion first wins.
        Route route = GRAPH.route(BrewGraph.WATER, s("night_vision"));
        assertEquals(List.of("minecraft:nether_wart", "minecraft:golden_carrot", "minecraft:gunpowder"),
                reagents(route));
    }

    @Test
    void sameBottleIsAnEmptyRouteAndUnreachableIsNull() {
        assertTrue(GRAPH.route(p("awkward"), p("awkward")).steps().isEmpty());
        assertNull(GRAPH.route(s("night_vision"), p("night_vision")));
    }

    @Test
    void routesFromMidChain() {
        Route route = GRAPH.route(p("night_vision"), s("invisibility"));
        assertEquals(List.of("minecraft:fermented_spider_eye", "minecraft:gunpowder"), reagents(route));
    }

    @Test
    void allRoutesAtOnce() {
        Map<String, Route> routes = GRAPH.routes(GRAPH.defaultStarts());
        for (String bottle : GRAPH.bottles()) {
            assertNotNull(routes.get(bottle), bottle + " should be reachable");
        }
        assertEquals(3, routes.get(s("night_vision")).steps().size());
    }

    @Test
    void lookups() {
        assertEquals(3, GRAPH.edgesFrom(p("night_vision")).size());
        assertEquals(2, GRAPH.edgesInto(s("night_vision")).size());
        assertEquals(4, GRAPH.edgesUsing("minecraft:gunpowder").size());
        assertEquals(List.of(BrewGraph.WATER), GRAPH.roots());
    }

    @Test
    void pickReagentsSharesItemsAcrossSteps() {
        Route route = new BrewGraph(List.of(
                e(p("water"), p("a"), "sugar"),
                e(p("a"), p("b"), "sugar"))).route(BrewGraph.WATER, p("b"));
        assertEquals(Arrays.asList("minecraft:sugar", null), BrewGraph.pickReagents(route, Map.of("minecraft:sugar", 1)));
        assertFalse(BrewGraph.canBrew(route, Map.of("minecraft:sugar", 1)));
        assertTrue(BrewGraph.canBrew(route, Map.of("minecraft:sugar", 2)));
        assertFalse(BrewGraph.canBrew(null, Map.of()));
        assertEquals(Map.of("minecraft:sugar", 2), BrewGraph.shoppingList(route));
    }

    @Test
    void pickReagentsPrefersTheAlternativeYouCarry() {
        Route route = new BrewGraph(List.of(e(p("water"), p("a"), "sugar", "honey_bottle")))
                .route(BrewGraph.WATER, p("a"));
        assertEquals(List.of("minecraft:honey_bottle"),
                BrewGraph.pickReagents(route, Map.of("minecraft:honey_bottle", 4)));
    }

    @Test
    void anyPotionRecipesApplyToEveryPotionOfThatItem() {
        BrewGraph graph = new BrewGraph(List.of(
                e(p("water"), p("awkward"), "nether_wart"),
                e(BrewGraph.key(POTION, BrewGraph.ANY), BrewGraph.key(POTION, "test:mud"), "dirt")));
        Route route = graph.route(BrewGraph.WATER, BrewGraph.key(POTION, "test:mud"));
        assertEquals(1, route.steps().size());
        assertEquals(BrewGraph.WATER, route.steps().get(0).from());
        assertEquals(1, graph.edgesFrom(p("awkward")).size());
        assertEquals(2, graph.edgesFrom(BrewGraph.WATER).size());
        assertFalse(graph.bottles().contains(BrewGraph.key(POTION, BrewGraph.ANY)));
    }
}
