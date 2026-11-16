package my.celium.org.brewingplus.stand;

import my.celium.org.brewingplus.BrewingPlusMod;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Server to client: a brew the player started has finished. */
public record ReadyPacket(StandInfo stand) implements CustomPacketPayload {
    public static final Type<ReadyPacket> TYPE = new Type<>(Identifier.fromNamespaceAndPath(BrewingPlusMod.ID, "ready"));
    public static final StreamCodec<FriendlyByteBuf, ReadyPacket> CODEC = StreamCodec.of(
            (buf, packet) -> packet.stand.write(buf), buf -> new ReadyPacket(StandInfo.read(buf)));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
