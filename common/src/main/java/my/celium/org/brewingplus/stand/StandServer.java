package my.celium.org.brewingplus.stand;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import my.celium.org.brewingplus.BrewingPlusMod;
import my.celium.org.brewingplus.BrewGraph;
import my.celium.org.brewingplus.BrewingStats;
import my.celium.org.brewingplus.mixin.BrewingStandAccessor;
import my.celium.org.network.MycelNetwork;
import my.celium.org.network.PacketDefinition;
import my.celium.org.util.ChatVoice;
import my.celium.org.util.TextUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.BrewingStandMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BrewingStandBlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;

/**
 * The server part, optional (always there in singleplayer). It remembers who last opened each brewing
 * stand and tells them when a brew there finishes, and once a second it sends players with the mod the
 * stands near them for the look-at card. It only reads the stands; brewing is never changed.
 */
public final class StandServer {
    static final ChatVoice VOICE = TextUtil.voice("Brewing+");
    /** Stands within this many blocks of a player go into the look-at data. */
    static final int RANGE = 8;
    /** A stand that has been idle this long (ticks) is forgotten. */
    static final int FORGET_AFTER = 20 * 60 * 10;

    public static PacketDefinition<StandPacket> stands;
    public static PacketDefinition<ReadyPacket> ready;

    private static final Map<Key, Watch> WATCHED = new HashMap<>();

    private record Key(ResourceKey<Level> dimension, BlockPos pos) {
    }

    private static final class Watch {
        UUID owner;
        int lastBrew;
        List<String> lastBottles = List.of();
        int idle;
    }

    private StandServer() {
    }

    /** End of every server tick, from each loader's tick event. */
    public static void tick(MinecraftServer server) {
        if (server == null || !BrewingPlusMod.isModEnabled()) {
            return;
        }
        try {
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                if (player.containerMenu instanceof BrewingStandMenu menu
                        && menu.getSlot(0).container instanceof BrewingStandBlockEntity stand && stand.getLevel() != null) {
                    Watch watch = WATCHED.computeIfAbsent(new Key(stand.getLevel().dimension(), stand.getBlockPos().immutable()),
                            key -> new Watch());
                    watch.owner = player.getUUID();
                }
            }
            checkWatched(server);
            if (server.getTickCount() % 20 == 0 && BrewingPlusMod.standCard.get()) {
                sendNearby(server);
            }
        } catch (RuntimeException e) {
            // A notice is a nicety; never break the server tick.
        }
    }

    private static void checkWatched(MinecraftServer server) {
        Iterator<Map.Entry<Key, Watch>> it = WATCHED.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Key, Watch> entry = it.next();
            ServerLevel level = server.getLevel(entry.getKey().dimension());
            BlockPos pos = entry.getKey().pos();
            if (level == null || !level.isLoaded(pos)) {
                continue;
            }
            if (!(level.getBlockEntity(pos) instanceof BrewingStandBlockEntity stand)) {
                it.remove();
                continue;
            }
            Watch watch = entry.getValue();
            int brew = ((BrewingStandAccessor) stand).brewingplus$brewTime();
            List<String> bottles = bottles(stand);
            if (watch.lastBrew > 0 && brew == 0 && !bottles.equals(watch.lastBottles) && hasAny(bottles)) {
                finished(server, level, stand, watch.owner);
            }
            watch.lastBrew = brew;
            watch.lastBottles = bottles;
            watch.idle = brew > 0 ? 0 : watch.idle + 1;
            if (watch.idle > FORGET_AFTER) {
                it.remove();
            }
        }
    }

    private static void finished(MinecraftServer server, ServerLevel level, BrewingStandBlockEntity stand, UUID owner) {
        ServerPlayer player = owner == null ? null : server.getPlayerList().getPlayer(owner);
        if (player == null || !BrewingPlusMod.readyMessage.get()) {
            return;
        }
        // Someone with this very stand open sees it happen.
        if (player.containerMenu instanceof BrewingStandMenu menu && menu.getSlot(0).container == stand) {
            return;
        }
        BrewingStats.count("ready_message");
        StandInfo info = info(stand);
        if (MycelNetwork.canSend(player, ready)) {
            MycelNetwork.sendToPlayer(player, ready, new ReadyPacket(info));
            return;
        }
        // Without the mod on the client: a plain chat line from the server.
        player.sendSystemMessage(VOICE.note(readyText(stand), TextUtil.muted("   " + where(player, level, stand.getBlockPos()))));
    }

    private static MutableComponent readyText(BrewingStandBlockEntity stand) {
        ItemStack first = ItemStack.EMPTY;
        int count = 0;
        for (int i = 0; i < 3; i++) {
            ItemStack bottle = stand.getItem(i);
            if (!bottle.isEmpty()) {
                count++;
                if (first.isEmpty()) {
                    first = bottle;
                }
            }
        }
        return Component.empty().append(Component.literal(count + " x ")).append(first.getHoverName().copy())
                .append(Component.literal(count == 1 ? " is ready" : " are ready"));
    }

    /** "12 blocks north", or "in another dimension". */
    static String where(ServerPlayer player, ServerLevel level, BlockPos pos) {
        if (player.level() != level) {
            return "in another dimension";
        }
        double dx = pos.getX() + 0.5 - player.getX();
        double dz = pos.getZ() + 0.5 - player.getZ();
        int distance = (int) Math.round(Math.sqrt(dx * dx + dz * dz));
        if (distance < 2) {
            return "right here";
        }
        String dir = Math.abs(dx) > Math.abs(dz) * 2 ? (dx > 0 ? "east" : "west")
                : Math.abs(dz) > Math.abs(dx) * 2 ? (dz > 0 ? "south" : "north")
                : (dz > 0 ? "south" : "north") + (dx > 0 ? "east" : "west");
        return distance + " blocks " + dir;
    }

    private static void sendNearby(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (!MycelNetwork.canSend(player, stands)) {
                continue;
            }
            ServerLevel level = player.level();
            List<BrewingStandBlockEntity> found = new ArrayList<>();
            int cx = player.getBlockX() >> 4;
            int cz = player.getBlockZ() >> 4;
            for (int x = cx - 1; x <= cx + 1; x++) {
                for (int z = cz - 1; z <= cz + 1; z++) {
                    LevelChunk chunk = level.getChunkSource().getChunkNow(x, z);
                    if (chunk == null) {
                        continue;
                    }
                    for (BlockEntity entity : chunk.getBlockEntities().values()) {
                        if (entity instanceof BrewingStandBlockEntity stand
                                && stand.getBlockPos().closerToCenterThan(player.position(), RANGE)) {
                            found.add(stand);
                        }
                    }
                }
            }
            if (found.isEmpty()) {
                continue;
            }
            found.sort(Comparator.comparingDouble(stand -> stand.getBlockPos().distToCenterSqr(player.position())));
            List<StandInfo> list = new ArrayList<>();
            for (BrewingStandBlockEntity stand : found) {
                if (list.size() >= StandPacket.MAX) {
                    break;
                }
                list.add(info(stand));
            }
            MycelNetwork.sendToPlayer(player, stands, new StandPacket(list));
        }
    }

    static StandInfo info(BrewingStandBlockEntity stand) {
        BrewingStandAccessor fields = (BrewingStandAccessor) stand;
        ItemStack fuel = stand.getItem(4);
        int brews = fields.brewingplus$fuel() + (fuel.isEmpty() ? 0 : fuel.getCount() * 20);
        ItemStack ingredient = stand.getItem(3);
        return new StandInfo(stand.getBlockPos().immutable(), fields.brewingplus$brewTime(), brews, bottles(stand),
                ingredient.isEmpty() ? "" : BuiltInRegistries.ITEM.getKey(ingredient.getItem()).toString());
    }

    static List<String> bottles(BrewingStandBlockEntity stand) {
        List<String> out = new ArrayList<>(3);
        for (int i = 0; i < 3; i++) {
            out.add(key(stand.getItem(i)));
        }
        return out;
    }

    /** Same key as the guide's BrewingData.keyOf, on the server: {@code item|potion}. */
    static String key(ItemStack stack) {
        if (stack.isEmpty()) {
            return "";
        }
        String item = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        PotionContents contents = stack.get(DataComponents.POTION_CONTENTS);
        String potion = contents == null ? "" : contents.potion().map(Holder::getRegisteredName).orElse("");
        return BrewGraph.key(item, potion);
    }

    private static boolean hasAny(List<String> bottles) {
        return bottles.stream().anyMatch(bottle -> !Objects.equals(bottle, ""));
    }

    /** Forgets everything when the server stops. */
    public static void clear() {
        WATCHED.clear();
    }
}
