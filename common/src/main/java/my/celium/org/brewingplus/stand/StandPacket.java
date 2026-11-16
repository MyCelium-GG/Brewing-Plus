package my.celium.org.brewingplus.stand;

import java.util.ArrayList;
import java.util.List;

import my.celium.org.brewingplus.BrewingPlusMod;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Server to client, once a second: the brewing stands near the player. */
public record StandPacket(List<StandInfo> stands) implements CustomPacketPayload {
    public static final int MAX = 32;
    public static final Type<StandPacket> TYPE = new Type<>(Identifier.fromNamespaceAndPath(BrewingPlusMod.ID, "stands"));
    public static final StreamCodec<FriendlyByteBuf, StandPacket> CODEC = StreamCodec.of(
            (buf, packet) -> {
                buf.writeVarInt(packet.stands.size());
                for (StandInfo info : packet.stands) {
                    info.write(buf);
                }
            },
            buf -> {
                int size = Math.min(MAX, buf.readVarInt());
                List<StandInfo> list = new ArrayList<>(size);
                for (int i = 0; i < size; i++) {
                    list.add(StandInfo.read(buf));
                }
                return new StandPacket(list);
            });

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
