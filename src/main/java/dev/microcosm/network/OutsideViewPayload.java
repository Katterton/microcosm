package dev.microcosm.network;

import dev.microcosm.Microcosm;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Server to client: the blocks around the cell of the room the player is in, one level up, to draw them 16 times
 * enlarged around the room.
 *
 * @param roomOrigin minimum corner of the player's room
 * @param cellPos    the room's cell, one level up
 * @param radius     how many blocks around the cell are included; 0 clears the view
 * @param states     block state ids, index {@code ((dy + r) * size + (dz + r)) * size + (dx + r)}
 */
public record OutsideViewPayload(BlockPos roomOrigin, BlockPos cellPos, int radius, int[] states) implements CustomPacketPayload {
    public static final Type<OutsideViewPayload> TYPE = new Type<>(Microcosm.id("outside_view"));

    public static final StreamCodec<RegistryFriendlyByteBuf, OutsideViewPayload> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeBlockPos(p.roomOrigin);
                buf.writeBlockPos(p.cellPos);
                buf.writeVarInt(p.radius);
                buf.writeVarIntArray(p.states);
            },
            buf -> new OutsideViewPayload(buf.readBlockPos(), buf.readBlockPos(), buf.readVarInt(), buf.readVarIntArray()));

    public static OutsideViewPayload clear() {
        return new OutsideViewPayload(BlockPos.ZERO, BlockPos.ZERO, 0, new int[0]);
    }

    public int size() {
        return radius * 2 + 1;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
