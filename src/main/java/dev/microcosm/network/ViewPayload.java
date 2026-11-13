package dev.microcosm.network;

import dev.microcosm.Microcosm;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.item.ItemStack;

/**
 * Server to client: what a viewer currently shows. Sent when a viewer is opened and then periodically.
 *
 * @param states    block state ids of the 16x16x16 room (index {@code (y * 16 + z) * 16 + x}); empty if unknown
 * @param dataAge   ticks since the room data was captured (relevant for scans)
 * @param status    translation key suffix for a status line, empty for none
 * @param actuator  tier of the actuator next to the same cell, 0 if there is none
 */
public record ViewPayload(
        BlockPos viewerPos,
        int viewerTier,
        int depth,
        int pathLength,
        int[] states,
        int dataAge,
        boolean scanning,
        int actuator,
        List<ItemStack> items,
        int queue,
        String lastResult,
        int energy,
        int capacity,
        int actuatorEnergy,
        int actuatorCapacity,
        String status
) implements CustomPacketPayload {
    public static final Type<ViewPayload> TYPE = new Type<>(Microcosm.id("view"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ViewPayload> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeBlockPos(p.viewerPos);
                buf.writeVarInt(p.viewerTier);
                buf.writeVarInt(p.depth);
                buf.writeVarInt(p.pathLength);
                buf.writeVarIntArray(p.states);
                buf.writeVarInt(p.dataAge);
                buf.writeBoolean(p.scanning);
                buf.writeVarInt(p.actuator);
                ItemStack.OPTIONAL_LIST_STREAM_CODEC.encode(buf, p.items);
                buf.writeVarInt(p.queue);
                buf.writeUtf(p.lastResult);
                buf.writeVarInt(p.energy);
                buf.writeVarInt(p.capacity);
                buf.writeVarInt(p.actuatorEnergy);
                buf.writeVarInt(p.actuatorCapacity);
                buf.writeUtf(p.status);
            },
            buf -> new ViewPayload(buf.readBlockPos(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(),
                    buf.readVarIntArray(), buf.readVarInt(), buf.readBoolean(), buf.readVarInt(),
                    ItemStack.OPTIONAL_LIST_STREAM_CODEC.decode(buf), buf.readVarInt(), buf.readUtf(),
                    buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readUtf()));

    public boolean hasRoom() {
        return states.length == 16 * 16 * 16;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
