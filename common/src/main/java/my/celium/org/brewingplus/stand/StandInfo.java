package my.celium.org.brewingplus.stand;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;

/**
 * One brewing stand as the server sees it.
 *
 * @param pos        where it stands
 * @param brewTime   ticks until the current brew finishes, 0 when idle
 * @param fuelBrews  brews left: the current blaze powder plus the powder in the fuel slot
 * @param bottles    the three bottle slots as {@code item|potion} keys, "" for empty
 * @param ingredient the ingredient's item id, "" for none
 */
public record StandInfo(BlockPos pos, int brewTime, int fuelBrews, List<String> bottles, String ingredient) {

    public void write(FriendlyByteBuf buf) {
        buf.writeBlockPos(pos);
        buf.writeVarInt(Math.max(0, brewTime));
        buf.writeVarInt(Math.max(0, fuelBrews));
        for (int i = 0; i < 3; i++) {
            buf.writeUtf(i < bottles.size() ? bottles.get(i) : "", 256);
        }
        buf.writeUtf(ingredient, 256);
    }

    public static StandInfo read(FriendlyByteBuf buf) {
        BlockPos pos = buf.readBlockPos();
        int brew = buf.readVarInt();
        int fuel = buf.readVarInt();
        List<String> bottles = new ArrayList<>(3);
        for (int i = 0; i < 3; i++) {
            bottles.add(buf.readUtf(256));
        }
        return new StandInfo(pos, brew, fuel, bottles, buf.readUtf(256));
    }

    /** The bottles that are there. */
    public List<String> filled() {
        List<String> out = new ArrayList<>();
        for (String bottle : bottles) {
            if (!bottle.isEmpty()) {
                out.add(bottle);
            }
        }
        return out;
    }
}
