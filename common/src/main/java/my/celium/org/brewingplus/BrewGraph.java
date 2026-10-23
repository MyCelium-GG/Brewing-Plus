package my.celium.org.brewingplus;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;

/**
 * Every brewing recipe as a graph: nodes are bottles ({@code item|potion},
 * e.g. {@code minecraft:splash_potion|minecraft:night_vision}), edges are
 * "bottle + reagent -> bottle". Since 26.x brewing is data-driven, so the
 * graph is built from whatever recipes the world (or vanilla) has.
 *
 * <p>{@link #routes} finds the cheapest chain from a start bottle (normally a
 * water bottle) to every reachable bottle: fewest steps, and among equally
 * short chains the one that stays a drinkable potion longest, so splash and
 * lingering conversions come last like players brew them.
 *
 * <p>Pure logic over string ids - no registries, safe for headless tests.
 */
public final class BrewGraph {
    /** The plain drinkable bottle; steps taken on any other bottle cost a bit more. */
    public static final String BASE_ITEM = "minecraft:potion";
    public static final String WATER = key(BASE_ITEM, "minecraft:water");
    /** Potion part of a key for recipes that accept any potion of an item. */
    public static final String ANY = "*";
    static final int STEP_COST = 10;
    static final int OFF_BASE_COST = 1;

    /**
     * One recipe applied to one concrete input: {@code from + reagent -> to}.
     * {@code reagents} lists every item the recipe accepts (a tag can allow several).
     */
    public record Edge(String from, List<String> reagents, String to, String recipe) {
        public Edge {
            reagents = List.copyOf(reagents);
        }
    }

    /** A chain of steps from {@code start} to {@code target}; empty when they are the same bottle. */
    public record Route(String start, String target, List<Edge> steps) {
        public Route {
            steps = List.copyOf(steps);
        }
    }

    private final List<Edge> edges;
    private final Map<String, List<Edge>> outgoing = new HashMap<>();
    private final Map<String, List<Edge>> incoming = new HashMap<>();
    private final Map<String, List<Edge>> byReagent = new LinkedHashMap<>();
    private final Set<String> bottles = new LinkedHashSet<>();

    public BrewGraph(List<Edge> edges) {
        this.edges = List.copyOf(edges);
        for (Edge edge : this.edges) {
            outgoing.computeIfAbsent(edge.from(), k -> new ArrayList<>()).add(edge);
            incoming.computeIfAbsent(edge.to(), k -> new ArrayList<>()).add(edge);
            for (String reagent : edge.reagents()) {
                byReagent.computeIfAbsent(reagent, k -> new ArrayList<>()).add(edge);
            }
            if (!isAny(edge.from())) {
                bottles.add(edge.from());
            }
            bottles.add(edge.to());
        }
    }

    public static String key(String item, String potion) {
        return item + "|" + (potion == null ? "" : potion);
    }

    public static String itemOf(String key) {
        int bar = key.indexOf('|');
        return bar < 0 ? key : key.substring(0, bar);
    }

    public static String potionOf(String key) {
        int bar = key.indexOf('|');
        return bar < 0 ? "" : key.substring(bar + 1);
    }

    public static boolean isAny(String key) {
        return ANY.equals(potionOf(key));
    }

    public List<Edge> edges() {
        return edges;
    }

    /** Every concrete bottle in recipe order (inputs and outputs, no "any potion" keys). */
    public Set<String> bottles() {
        return Collections.unmodifiableSet(bottles);
    }

    /** Every reagent item id in recipe order. */
    public Set<String> reagents() {
        return Collections.unmodifiableSet(byReagent.keySet());
    }

    public boolean isEmpty() {
        return edges.isEmpty();
    }

    /** Steps that can be taken from {@code bottle}, including "any potion of this item" recipes. */
    public List<Edge> edgesFrom(String bottle) {
        List<Edge> out = new ArrayList<>(outgoing.getOrDefault(bottle, List.of()));
        if (!isAny(bottle)) {
            for (Edge edge : outgoing.getOrDefault(key(itemOf(bottle), ANY), List.of())) {
                out.add(new Edge(bottle, edge.reagents(), edge.to(), edge.recipe()));
            }
        }
        return out;
    }

    /** Every recipe that makes {@code bottle}. */
    public List<Edge> edgesInto(String bottle) {
        return List.copyOf(incoming.getOrDefault(bottle, List.of()));
    }

    /** Every recipe that takes {@code reagent}. */
    public List<Edge> edgesUsing(String reagent) {
        return List.copyOf(byReagent.getOrDefault(reagent, List.of()));
    }

    /** Bottles nothing brews into (besides water): where datapack chains may start. */
    public List<String> roots() {
        List<String> roots = new ArrayList<>();
        for (String bottle : bottles) {
            if (!incoming.containsKey(bottle)) {
                roots.add(bottle);
            }
        }
        return roots;
    }

    /** Water first, then every other root. */
    public List<String> defaultStarts() {
        List<String> starts = new ArrayList<>();
        starts.add(WATER);
        for (String root : roots()) {
            if (!root.equals(WATER)) {
                starts.add(root);
            }
        }
        return starts;
    }

    /**
     * Cheapest route from any of {@code starts} to every reachable bottle
     * (Dijkstra). Starts map to empty routes. Ties go to the alphabetically
     * first bottle so results never depend on hash order.
     */
    public Map<String, Route> routes(Collection<String> starts) {
        Map<String, Integer> cost = new HashMap<>();
        Map<String, Edge> via = new HashMap<>();
        Map<String, String> origin = new HashMap<>();
        PriorityQueue<Map.Entry<String, Integer>> queue = new PriorityQueue<>(
                Comparator.<Map.Entry<String, Integer>>comparingInt(Map.Entry::getValue)
                        .thenComparing(Map.Entry::getKey));
        for (String start : starts) {
            if (!cost.containsKey(start)) {
                cost.put(start, 0);
                origin.put(start, start);
                queue.add(Map.entry(start, 0));
            }
        }
        while (!queue.isEmpty()) {
            Map.Entry<String, Integer> head = queue.poll();
            String bottle = head.getKey();
            if (head.getValue() > cost.get(bottle)) {
                continue;
            }
            int stepCost = STEP_COST + (BASE_ITEM.equals(itemOf(bottle)) ? 0 : OFF_BASE_COST);
            for (Edge edge : edgesFrom(bottle)) {
                int next = head.getValue() + stepCost;
                Integer known = cost.get(edge.to());
                if (known == null || next < known) {
                    cost.put(edge.to(), next);
                    via.put(edge.to(), edge);
                    origin.put(edge.to(), origin.get(bottle));
                    queue.add(Map.entry(edge.to(), next));
                }
            }
        }
        Map<String, Route> routes = new LinkedHashMap<>();
        for (String bottle : cost.keySet()) {
            List<Edge> steps = new ArrayList<>();
            String at = bottle;
            while (via.containsKey(at) && !at.equals(origin.get(bottle))) {
                Edge edge = via.get(at);
                steps.add(edge);
                at = edge.from();
            }
            Collections.reverse(steps);
            routes.put(bottle, new Route(origin.get(bottle), bottle, steps));
        }
        return routes;
    }

    /** The cheapest route from one bottle to another, or {@code null} when there is none. */
    public Route route(String from, String to) {
        return routes(List.of(from)).get(to);
    }

    /**
     * For each step of {@code route}, the reagent to use given what the
     * player carries ({@code counts} by item id), or {@code null} when none of
     * the step's reagents is left. One reagent is spent per step (it brews up
     * to three bottles at once); items are shared across steps.
     */
    public static List<String> pickReagents(Route route, Map<String, Integer> counts) {
        Map<String, Integer> left = new HashMap<>(counts);
        List<String> picks = new ArrayList<>();
        for (Edge step : route.steps()) {
            String best = null;
            int bestCount = 0;
            for (String reagent : step.reagents()) {
                int count = left.getOrDefault(reagent, 0);
                if (count > bestCount) {
                    best = reagent;
                    bestCount = count;
                }
            }
            if (best != null) {
                left.put(best, bestCount - 1);
            }
            picks.add(best);
        }
        return picks;
    }

    /** Whether the player carries a reagent for every step of {@code route}. */
    public static boolean canBrew(Route route, Map<String, Integer> counts) {
        return route != null && !pickReagents(route, counts).contains(null);
    }

    /** Reagents a route needs, merged by recipe choice: first accepted item -> count. */
    public static Map<String, Integer> shoppingList(Route route) {
        Map<String, Integer> list = new LinkedHashMap<>();
        for (Edge step : route.steps()) {
            if (!step.reagents().isEmpty()) {
                list.merge(step.reagents().get(0), 1, Integer::sum);
            }
        }
        return list;
    }
}
