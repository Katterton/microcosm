package dev.microcosm.network;

import dev.microcosm.Microcosm;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Client to server: an input from the viewer screen.
 *
 * @param op     what to do
 * @param local  room position for {@link Op#ZOOM_IN} and {@link Op#ACTION}
 * @param action {@link dev.microcosm.device.DeviceAction.Type} ordinal for {@link Op#ACTION}
 * @param slot   actuator slot for {@link Op#ACTION}
 */
public record ViewerRequestPayload(Op op, BlockPos local, int action, int slot) implements CustomPacketPayload {
    public enum Op {
        ZOOM_IN,
        ZOOM_OUT,
        ACTION,
        SCAN,
        CLOSE
    }

    public static final Type<ViewerRequestPayload> TYPE = new Type<>(Microcosm.id("viewer_request"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ViewerRequestPayload> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeEnum(p.op);
                buf.writeBlockPos(p.local);
                buf.writeVarInt(p.action);
                buf.writeVarInt(p.slot);
            },
            buf -> new ViewerRequestPayload(buf.readEnum(Op.class), buf.readBlockPos(), buf.readVarInt(), buf.readVarInt()));

    public static ViewerRequestPayload simple(Op op) {
        return new ViewerRequestPayload(op, BlockPos.ZERO, 0, 0);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
