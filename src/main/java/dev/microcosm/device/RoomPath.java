package dev.microcosm.device;

import dev.microcosm.world.MicroBlocks;
import dev.microcosm.world.MicrocosmLevels;
import dev.microcosm.world.RoomManager;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import org.jspecify.annotations.Nullable;

/**
 * A chain of nested blocks starting at a device: the first entry is the block the device works on, every further
 * entry is a block inside the previous block's room. The path leads to the room of its last block.
 *
 * <p>Blocks on the path may still be ordinary blocks. {@link #view} treats them as rooms full of their material
 * without changing the world; {@link #resolve} divides them first, for actions that change something.</p>
 */
public final class RoomPath {
    private RoomPath() {}

    /** The block below a viewer, which is the only place a viewer looks into. */
    public static @Nullable BlockPos targetBelow(ServerLevel level, BlockPos viewerPos) {
        BlockPos pos = viewerPos.below();
        return MicroBlocks.canOpen(level, pos) ? pos : null;
    }

    /** The block a manipulator works on: the one it points at (or, for the field manipulator, the one below). */
    public static @Nullable BlockPos actuatorTarget(ServerLevel level, BlockPos devicePos) {
        BlockState state = level.getBlockState(devicePos);
        Direction facing = state.hasProperty(BlockStateProperties.FACING) ? state.getValue(BlockStateProperties.FACING) : Direction.DOWN;
        BlockPos pos = devicePos.relative(facing);
        return MicroBlocks.canOpen(level, pos) ? pos : null;
    }

    /**
     * A room as a viewer sees it.
     *
     * @param uniform if not null, the room's block is still an ordinary block and the room is entirely this material
     */
    public record View(RoomManager.Room room, @Nullable BlockState uniform) {
        public BlockState stateAt(BlockPos pos) {
            return uniform != null ? uniform : room.level().getBlockState(pos);
        }
    }

    /** The room the path leads to as it can be seen right now, without dividing anything. */
    public static @Nullable View view(ServerLevel level, List<BlockPos> path) {
        if (path.isEmpty()) {
            return null;
        }
        ServerLevel current = level;
        View view = null;
        for (BlockPos pos : path) {
            if (view != null && !contains(view.room(), pos)) {
                return null;
            }
            if (MicrocosmLevels.depth(current) >= MicrocosmLevels.MAX_DEPTH || !current.isLoaded(pos)) {
                return null;
            }
            BlockState state = view != null ? view.stateAt(pos) : current.getBlockState(pos);
            boolean micro = view == null || view.uniform() == null ? MicroBlocks.isMicro(state) : false;
            if (!micro && !MicroBlocks.isDivisible(state)) {
                return null;
            }
            RoomManager.Room room = RoomManager.roomOf(current, pos, true);
            if (room == null) {
                return null;
            }
            view = new View(room, micro ? null : state);
            current = room.level();
        }
        return view;
    }

    /** The room the path leads to, dividing ordinary blocks along the way. Null if a step no longer fits. */
    public static RoomManager.@Nullable Room resolve(ServerLevel level, List<BlockPos> path) {
        if (path.isEmpty()) {
            return null;
        }
        ServerLevel current = level;
        RoomManager.Room room = null;
        for (BlockPos pos : path) {
            if (room != null && !contains(room, pos)) {
                return null;
            }
            room = MicroBlocks.open(current, pos);
            if (room == null) {
                return null;
            }
            current = room.level();
        }
        return room;
    }

    public static boolean contains(RoomManager.Room room, BlockPos pos) {
        BlockPos min = room.origin();
        BlockPos max = room.max();
        return pos.getX() >= min.getX() && pos.getY() >= min.getY() && pos.getZ() >= min.getZ()
                && pos.getX() <= max.getX() && pos.getY() <= max.getY() && pos.getZ() <= max.getZ();
    }

    public static boolean isLocal(BlockPos local) {
        return local.getX() >= 0 && local.getY() >= 0 && local.getZ() >= 0
                && local.getX() < 16 && local.getY() < 16 && local.getZ() < 16;
    }
}
