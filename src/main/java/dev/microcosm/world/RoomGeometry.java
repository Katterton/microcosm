package dev.microcosm.world;

import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import org.jspecify.annotations.Nullable;

/**
 * Coordinate layout of a microcosm level.
 *
 * <p>Every 16x16x16 section of a parent level that contains a cell gets a region of 256x256x256 blocks in the
 * child level. Inside that region each cell's room sits at the same relative position as the cell sits inside
 * its section, so cells that touch in the parent level have rooms that touch in the child level. Regions are
 * laid out on a grid with a gap between them, which holds the boundary walls.</p>
 */
public final class RoomGeometry {
    public static final int ROOM = 16;
    public static final int REGION = ROOM * 16;
    public static final int SPACING = REGION + 32;
    public static final int GRID = 64;

    private RoomGeometry() {}

    public static BlockPos regionOrigin(int regionIndex) {
        return new BlockPos((regionIndex % GRID) * SPACING, 0, (regionIndex / GRID) * SPACING);
    }

    /** Origin (minimum corner) of the room belonging to the cell at {@code parentPos}. */
    public static BlockPos roomOrigin(int regionIndex, BlockPos parentPos) {
        return regionOrigin(regionIndex).offset(
                (parentPos.getX() & 15) * ROOM, (parentPos.getY() & 15) * ROOM, (parentPos.getZ() & 15) * ROOM);
    }

    public static boolean sameSection(BlockPos a, BlockPos b) {
        return SectionPos.blockToSectionCoord(a.getX()) == SectionPos.blockToSectionCoord(b.getX())
                && SectionPos.blockToSectionCoord(a.getY()) == SectionPos.blockToSectionCoord(b.getY())
                && SectionPos.blockToSectionCoord(a.getZ()) == SectionPos.blockToSectionCoord(b.getZ());
    }

    /** A room slot inside a region: region index plus slot coordinates 0..15 on each axis. */
    public record Slot(int regionIndex, int x, int y, int z) {
        public BlockPos origin() {
            return regionOrigin(regionIndex).offset(x * ROOM, y * ROOM, z * ROOM);
        }
    }

    /** Which room slot a child-level position lies in, or null if it is in the gap between regions. */
    public static @Nullable Slot slotAt(BlockPos pos) {
        int ix = Math.floorDiv(pos.getX(), SPACING);
        int iz = Math.floorDiv(pos.getZ(), SPACING);
        if (ix < 0 || ix >= GRID || iz < 0 || pos.getY() < 0 || pos.getY() >= REGION) {
            return null;
        }
        int lx = pos.getX() - ix * SPACING;
        int lz = pos.getZ() - iz * SPACING;
        if (lx >= REGION || lz >= REGION) {
            return null;
        }
        return new Slot(iz * GRID + ix, lx / ROOM, pos.getY() / ROOM, lz / ROOM);
    }
}
