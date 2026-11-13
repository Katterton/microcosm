package dev.microcosm.device;

import dev.microcosm.block.CellBlock;
import dev.microcosm.world.RoomManager;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import org.jspecify.annotations.Nullable;

/**
 * A chain of nested cells starting next to a device: the first entry is the cell next to the device, every further
 * entry is a cell inside the previous cell's room. The path leads to the room of its last cell.
 */
public final class RoomPath {
    private RoomPath() {}

    /** The cell next to {@code devicePos}, or null if there is none. */
    public static @Nullable BlockPos adjacentCell(ServerLevel level, BlockPos devicePos) {
        for (Direction dir : Direction.values()) {
            BlockPos pos = devicePos.relative(dir);
            if (level.isLoaded(pos) && level.getBlockState(pos).getBlock() instanceof CellBlock) {
                return pos;
            }
        }
        return null;
    }

    /** The room the path leads to, or null if any step is no longer a cell inside the previous room. */
    public static RoomManager.@Nullable Room resolve(ServerLevel level, List<BlockPos> path) {
        if (path.isEmpty()) {
            return null;
        }
        ServerLevel current = level;
        RoomManager.Room room = null;
        for (int i = 0; i < path.size(); i++) {
            BlockPos cell = path.get(i);
            if (room != null && !contains(room, cell)) {
                return null;
            }
            if (!current.isLoaded(cell) || !(current.getBlockState(cell).getBlock() instanceof CellBlock)) {
                return null;
            }
            room = RoomManager.roomOf(current, cell, true);
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
