package dev.microcosm.network;

import dev.microcosm.Microcosm;
import java.util.Map;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Server to client: which pool dimension is at which scale level. */
public record PoolSyncPayload(Map<Identifier, Integer> depths) implements CustomPacketPayload {
    public static final Type<PoolSyncPayload> TYPE = new Type<>(Microcosm.id("pool_sync"));
    private static final StreamCodec<io.netty.buffer.ByteBuf, java.util.HashMap<Identifier, Integer>> MAP =
            ByteBufCodecs.map(java.util.HashMap::new, Identifier.STREAM_CODEC, ByteBufCodecs.VAR_INT);
    public static final StreamCodec<RegistryFriendlyByteBuf, PoolSyncPayload> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> MAP.encode(buf, new java.util.HashMap<>(p.depths())),
            buf -> new PoolSyncPayload(MAP.decode(buf)));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
