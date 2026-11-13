package dev.microcosm.world;

import dev.microcosm.block.CellBlockEntity;
import dev.microcosm.block.PortBlock;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.BlockCapability;
import net.neoforged.neoforge.capabilities.IBlockCapabilityProvider;
import org.jspecify.annotations.Nullable;

/** Resolves the connection between a port inside a room and its cell one level up, and tunnels capabilities. */
public final class PortLinks {
    /** Guards against capability lookups that loop through several ports and cells. */
    private static final ThreadLocal<int[]> LOOKUP_DEPTH = ThreadLocal.withInitial(() -> new int[1]);
    private static final int MAX_LOOKUP_DEPTH = 8;

    private PortLinks() {}

    /** A port's cell: its level, position, block entity, and the side of the cell the port belongs to. */
    public record Link(ServerLevel parent, BlockPos cellPos, CellBlockEntity cell, Direction face) {}

    /** A port inside a room. */
    public record PortRef(ServerLevel level, BlockPos pos, BlockState state) {
        public PortBlock.Mode mode() {
            return state.getValue(PortBlock.MODE);
        }
    }

    /** Whether {@code pos} lies in the layer of its room that touches the room's {@code side}. */
    public static boolean isOnRoomSide(BlockPos pos, Direction side) {
        RoomGeometry.Slot slot = RoomGeometry.slotAt(pos);
        if (slot == null) {
            return false;
        }
        BlockPos local = pos.subtract(slot.origin());
        int last = RoomGeometry.ROOM - 1;
        return switch (side) {
            case DOWN -> local.getY() == 0;
            case UP -> local.getY() == last;
            case NORTH -> local.getZ() == 0;
            case SOUTH -> local.getZ() == last;
            case WEST -> local.getX() == 0;
            case EAST -> local.getX() == last;
        };
    }

    /** The cell a port at {@code pos} facing {@code facing} belongs to, or null if it is not on that room side. */
    public static @Nullable Link link(ServerLevel child, BlockPos pos, Direction facing) {
        if (!isOnRoomSide(pos, facing)) {
            return null;
        }
        RoomManager.Parent parent = RoomManager.parentOf(child, pos);
        if (parent == null || !parent.level().isLoaded(parent.pos())
                || !(parent.level().getBlockEntity(parent.pos()) instanceof CellBlockEntity cell)) {
            return null;
        }
        return new Link(parent.level(), parent.pos(), cell, facing);
    }

    /** The active port on the given side of a cell's room, dropping stale registrations. */
    public static @Nullable PortRef portOn(ServerLevel parent, BlockPos cellPos, CellBlockEntity cell, Direction face) {
        BlockPos pos = cell.port(face);
        if (pos == null) {
            return null;
        }
        RoomManager.Room room = RoomManager.roomOf(parent, cellPos, false);
        if (room == null || !room.level().isLoaded(pos)) {
            return null;
        }
        BlockState state = room.level().getBlockState(pos);
        if (!(state.getBlock() instanceof PortBlock) || state.getValue(PortBlock.FACING) != face) {
            cell.setPort(face, null);
            return null;
        }
        return new PortRef(room.level(), pos, state);
    }

    /** Outside to inside: the cell's side exposes the block behind the port on that side. */
    public static <T> IBlockCapabilityProvider<T, @Nullable Direction> cellProvider(BlockCapability<T, @Nullable Direction> capability) {
        return (level, pos, state, blockEntity, side) -> {
            if (side == null || !(level instanceof ServerLevel parent) || !(blockEntity instanceof CellBlockEntity cell)) {
                return null;
            }
            PortRef port = portOn(parent, pos, cell, side);
            if (port == null) {
                return null;
            }
            BlockPos target = port.pos().relative(side.getOpposite());
            return guarded(() -> port.level().isLoaded(target) ? port.level().getCapability(capability, target, side) : null);
        };
    }

    /** Inside to outside: the port exposes the block next to the cell's matching side. */
    public static <T> IBlockCapabilityProvider<T, @Nullable Direction> portProvider(BlockCapability<T, @Nullable Direction> capability) {
        return (level, pos, state, blockEntity, side) -> {
            Direction facing = state.getValue(PortBlock.FACING);
            if (side == facing || !(level instanceof ServerLevel child)) {
                return null;
            }
            Link link = link(child, pos, facing);
            if (link == null) {
                return null;
            }
            BlockPos target = link.cellPos().relative(facing);
            return guarded(() -> link.parent().isLoaded(target)
                    ? link.parent().getCapability(capability, target, facing.getOpposite()) : null);
        };
    }

    private static <T> @Nullable T guarded(Supplier<@Nullable T> lookup) {
        int[] depth = LOOKUP_DEPTH.get();
        if (depth[0] >= MAX_LOOKUP_DEPTH) {
            return null;
        }
        depth[0]++;
        try {
            return lookup.get();
        } finally {
            depth[0]--;
        }
    }
}
