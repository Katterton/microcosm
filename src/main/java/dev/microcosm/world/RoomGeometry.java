package dev.microcosm.world;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

/**
 * Coordinate layout of the microcosm.
 *
 * <p>Each level one scale down mirrors the level above 16 times enlarged, contiguously: the room of the block at
 * parent position {@code p} starts at {@code (p - tileOrigin) * 16}. So neighbouring blocks have neighbouring rooms,
 * and walking, redstone, water or ender pearls cross from room to room as they would in the world above.</p>
 *
 * <p>Because a dimension is at most 4064 blocks tall, the parent world is cut into tiles of
 * {@link #TILE_WIDTH} x {@link #TILE_HEIGHT} x {@link #TILE_WIDTH} blocks; each tile in use gets one dimension from
 * a pool. Players pass from tile to tile at the borders.</p>
 */
public final class RoomGeometry {
    public static final int ROOM = 16;
    /** Parent blocks per tile horizontally (1 048 576 room blocks wide). */
    public static final int TILE_WIDTH = 1 << 16;
    /** Parent blocks per tile vertically: 2016 room blocks plus a wall above and below fit the 2048-block limit of a dimension. */
    public static final int TILE_HEIGHT = 126;

    private RoomGeometry() {}

    /** Identifies one tile of a parent level. */
    public record TileKey(int x, int y, int z) {
        public static TileKey of(BlockPos parentPos) {
            return new TileKey(Math.floorDiv(parentPos.getX(), TILE_WIDTH), Math.floorDiv(parentPos.getY(), TILE_HEIGHT),
                    Math.floorDiv(parentPos.getZ(), TILE_WIDTH));
        }

        /** Parent position of the tile's minimum corner. */
        public BlockPos origin() {
            return new BlockPos(x * TILE_WIDTH, y * TILE_HEIGHT, z * TILE_WIDTH);
        }
    }

    /** Minimum corner of the room of {@code parentPos} in its tile's dimension. */
    public static BlockPos roomOrigin(TileKey tile, BlockPos parentPos) {
        return parentPos.subtract(tile.origin()).multiply(ROOM);
    }

    /** The parent block whose room contains {@code childPos} (also outside the tile, for its neighbours). */
    public static BlockPos parentPos(TileKey tile, BlockPos childPos) {
        return tile.origin().offset(Math.floorDiv(childPos.getX(), ROOM), Math.floorDiv(childPos.getY(), ROOM),
                Math.floorDiv(childPos.getZ(), ROOM));
    }

    /** {@link #parentPos} for exact positions, in parent block units. */
    public static Vec3 parentVec(TileKey tile, Vec3 childPos) {
        return Vec3.atLowerCornerOf(tile.origin()).add(childPos.scale(1.0 / ROOM));
    }

    public static Vec3 childVec(TileKey tile, Vec3 parentPos) {
        return parentPos.subtract(Vec3.atLowerCornerOf(tile.origin())).scale(ROOM);
    }

    /** Minimum corner of the room containing {@code childPos}. */
    public static BlockPos slotOrigin(BlockPos childPos) {
        return new BlockPos(Math.floorDiv(childPos.getX(), ROOM) * ROOM, Math.floorDiv(childPos.getY(), ROOM) * ROOM,
                Math.floorDiv(childPos.getZ(), ROOM) * ROOM);
    }

    /** Whether a room position lies inside its tile (rather than in the border wall layer around it). */
    public static boolean insideTile(Vec3 childPos) {
        double width = (double) TILE_WIDTH * ROOM;
        return childPos.x >= 0 && childPos.z >= 0 && childPos.y >= 0
                && childPos.x < width && childPos.z < width && childPos.y < TILE_HEIGHT * ROOM;
    }

    /** Whether two parent blocks fall into the same tile, so their rooms touch. */
    public static boolean sameTile(BlockPos a, BlockPos b) {
        return TileKey.of(a).equals(TileKey.of(b));
    }
}
