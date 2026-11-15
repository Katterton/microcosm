package dev.microcosm.world;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.microcosm.Microcosm;
import dev.microcosm.ModRegistry;
import dev.microcosm.block.CellBlock;
import dev.microcosm.block.CellWallBlock;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.world.chunk.TicketController;
import org.jspecify.annotations.Nullable;

/**
 * Hands out pool dimensions to tiles, keeps room walls in sync with which blocks are divided, and keeps rooms
 * loaded while their block is loaded.
 */
public final class RoomManager extends SavedData {
    /**
     * Forces the chunk of each active room. Tickets are dropped on server start and re-added by cell block entities
     * as they load, so a room is only loaded while its parent cell is (which chains through all nested levels).
     */
    public static final TicketController TICKETS = new TicketController(Microcosm.id("rooms"), (level, helper) ->
            new ArrayList<>(helper.getBlockTickets().keySet()).forEach(helper::removeAllTickets));

    /** One tile of a parent level, mapped to a pool dimension. */
    private record Tile(Identifier parentDimension, int x, int y, int z, int depth, int slot) {
        static final Codec<Tile> CODEC = RecordCodecBuilder.create(i -> i.group(
                Identifier.CODEC.fieldOf("parent").forGetter(Tile::parentDimension),
                Codec.INT.fieldOf("x").forGetter(Tile::x),
                Codec.INT.fieldOf("y").forGetter(Tile::y),
                Codec.INT.fieldOf("z").forGetter(Tile::z),
                Codec.INT.fieldOf("depth").forGetter(Tile::depth),
                Codec.INT.fieldOf("slot").forGetter(Tile::slot)
        ).apply(i, Tile::new));

        RoomGeometry.TileKey key() {
            return new RoomGeometry.TileKey(x, y, z);
        }

        String id() {
            return id(parentDimension, key());
        }

        static String id(Identifier dim, RoomGeometry.TileKey key) {
            return dim + "|" + key.x() + "|" + key.y() + "|" + key.z();
        }
    }

    private static final Codec<RoomManager> CODEC = Tile.CODEC.listOf()
            .xmap(RoomManager::new, m -> m.tiles).fieldOf("tiles").codec();

    private static final SavedDataType<RoomManager> TYPE =
            new SavedDataType<>(Microcosm.id("rooms"), RoomManager::new, CODEC);

    private final List<Tile> tiles = new ArrayList<>();
    private final Map<String, Tile> byParentTile = new HashMap<>();
    private final Map<Integer, Tile> bySlot = new HashMap<>();

    private RoomManager() {}

    private RoomManager(List<Tile> loaded) {
        loaded.forEach(this::index);
    }

    private void index(Tile tile) {
        tiles.add(tile);
        byParentTile.put(tile.id(), tile);
        bySlot.put(tile.slot, tile);
        MicrocosmLevels.setDepth(MicrocosmLevels.poolKey(tile.slot), tile.depth);
    }

    public static RoomManager get(MinecraftServer server) {
        return server.getDataStorage().computeIfAbsent(TYPE);
    }

    /** Loads the tile assignments, so depths are known from the start. */
    public static void load(MinecraftServer server) {
        MicrocosmLevels.setAll(Map.of());
        RoomManager manager = get(server);
        manager.tiles.forEach(t -> MicrocosmLevels.setDepth(MicrocosmLevels.poolKey(t.slot), t.depth));
    }

    /** A block's room: the level it is in and the minimum corner of its 16x16x16 space. */
    public record Room(ServerLevel level, BlockPos origin) {
        public BlockPos max() {
            return origin.offset(RoomGeometry.ROOM - 1, RoomGeometry.ROOM - 1, RoomGeometry.ROOM - 1);
        }

        public Iterable<BlockPos> positions() {
            return BlockPos.betweenClosed(origin, max());
        }

        public BlockPos spawnPoint() {
            return origin.offset(RoomGeometry.ROOM / 2, 0, RoomGeometry.ROOM / 2);
        }
    }

    /** Where a room's block is, one level up. */
    public record Parent(ServerLevel level, BlockPos pos) {}

    /**
     * The room of the block at {@code pos}, or null at the deepest level, if its tile has no dimension yet and
     * {@code allocate} is false, or if the pool is used up.
     */
    public static @Nullable Room roomOf(ServerLevel parent, BlockPos pos, boolean allocate) {
        int depth = MicrocosmLevels.depth(parent) + 1;
        if (depth > MicrocosmLevels.MAX_DEPTH) {
            return null;
        }
        MinecraftServer server = parent.getServer();
        RoomManager manager = get(server);
        RoomGeometry.TileKey key = RoomGeometry.TileKey.of(pos);
        Tile tile = manager.byParentTile.get(Tile.id(parent.dimension().identifier(), key));
        if (tile == null) {
            if (!allocate) {
                return null;
            }
            tile = manager.allocate(server, parent.dimension().identifier(), key, depth);
            if (tile == null) {
                return null;
            }
        }
        ServerLevel child = server.getLevel(MicrocosmLevels.poolKey(tile.slot));
        return child == null ? null : new Room(child, RoomGeometry.roomOrigin(key, pos));
    }

    /** Hands a free pool dimension to a tile. */
    private @Nullable Tile allocate(MinecraftServer server, Identifier parentDim, RoomGeometry.TileKey key, int depth) {
        for (int slot = 0; ; slot++) {
            if (server.getLevel(MicrocosmLevels.poolKey(slot)) == null) {
                Microcosm.LOGGER.warn("All {} microcosm pool dimensions are in use; add more pool dimensions to allow more", slot);
                return null;
            }
            if (!bySlot.containsKey(slot)) {
                Tile tile = new Tile(parentDim, key.x(), key.y(), key.z(), depth, slot);
                index(tile);
                setDirty();
                PoolSync.broadcast(server);
                return tile;
            }
        }
    }

    /** The block that owns the room containing {@code pos}, if any (also for positions just outside the tile). */
    public static @Nullable Parent parentOf(ServerLevel child, BlockPos pos) {
        int slot = MicrocosmLevels.poolSlot(child.dimension());
        Tile tile = slot < 0 ? null : get(child.getServer()).bySlot.get(slot);
        if (tile == null) {
            return null;
        }
        ServerLevel parent = child.getServer().getLevel(ResourceKey.create(Registries.DIMENSION, tile.parentDimension));
        return parent == null ? null : new Parent(parent, RoomGeometry.parentPos(tile.key(), pos));
    }

    /** The exact position one level up that corresponds to {@code pos} here, for moving players between tiles. */
    public static @Nullable Vec3 parentVec(ServerLevel child, Vec3 pos) {
        int slot = MicrocosmLevels.poolSlot(child.dimension());
        Tile tile = slot < 0 ? null : get(child.getServer()).bySlot.get(slot);
        return tile == null ? null : RoomGeometry.parentVec(tile.key(), pos);
    }

    /** The exact room position for parent position {@code parentPos}, in the dimension of its tile. */
    public static @Nullable Room roomAt(ServerLevel parent, Vec3 parentPos, boolean allocate) {
        return roomOf(parent, BlockPos.containing(parentPos), allocate);
    }

    public static Vec3 childVec(Room room, ServerLevel parent, Vec3 parentPos) {
        BlockPos block = BlockPos.containing(parentPos);
        return Vec3.atLowerCornerOf(room.origin()).add(parentPos.subtract(Vec3.atLowerCornerOf(block)).scale(RoomGeometry.ROOM));
    }

    private static boolean isCell(Level level, BlockPos pos) {
        return level.getBlockState(pos).getBlock() instanceof CellBlock;
    }

    /** Neighbouring blocks in the same tile have rooms that touch, so the wall between them is left open. */
    private static boolean connected(Level parent, BlockPos pos, Direction dir) {
        BlockPos neighbor = pos.relative(dir);
        return RoomGeometry.sameTile(pos, neighbor) && isCell(parent, neighbor);
    }

    public static void onCellPlaced(ServerLevel parent, BlockPos pos) {
        Room room = roomOf(parent, pos, true);
        if (room == null) {
            return;
        }
        // Walls that used to separate this (inactive) slot from active neighbours sit on its inner faces.
        for (Direction dir : Direction.values()) {
            for (BlockPos p : innerFace(room, dir)) {
                if (room.level.getBlockState(p).is(ModRegistry.CELL_WALL.get())) {
                    room.level.setBlock(p, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
                }
            }
        }
        // Wall off every side that does not lead into a neighbouring room. Walls at the tile border let players
        // through: they are moved on to the next tile.
        for (Direction dir : Direction.values()) {
            if (!connected(parent, pos, dir)) {
                boolean border = !RoomGeometry.sameTile(pos, pos.relative(dir));
                for (BlockPos p : outerFace(room, dir)) {
                    placeWall(room.level, p, border);
                }
            }
        }
    }

    public static void onCellRemoved(ServerLevel parent, BlockPos pos) {
        Room room = roomOf(parent, pos, false);
        if (room == null) {
            return;
        }
        // Close the openings into rooms of neighbouring cells that still exist.
        for (Direction dir : Direction.values()) {
            if (connected(parent, pos, dir)) {
                for (BlockPos p : innerFace(room, dir)) {
                    placeWall(room.level, p, false);
                }
            }
        }
    }

    /** Places a wall block already connected to the walls around it; neighbours update themselves. */
    private static void placeWall(ServerLevel level, BlockPos pos, boolean passable) {
        BlockState wall = ModRegistry.CELL_WALL.get().connectedState(level, pos).setValue(CellWallBlock.PASSABLE, passable);
        level.setBlock(pos.immutable(), wall, Block.UPDATE_CLIENTS);
    }

    /** Re-connects the walls around a room, for walls placed before they had connections. */
    public static void refreshWalls(Room room) {
        CellWallBlock wall = ModRegistry.CELL_WALL.get();
        for (Direction dir : Direction.values()) {
            for (BlockPos p : outerFace(room, dir)) {
                BlockState state = room.level.getBlockState(p);
                if (state.getBlock() == wall) {
                    BlockState connected = wall.connectedState(room.level, p).setValue(CellWallBlock.PASSABLE, state.getValue(CellWallBlock.PASSABLE));
                    if (connected != state) {
                        room.level.setBlock(p.immutable(), connected, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
                    }
                }
            }
        }
    }

    public static boolean isRoomEmpty(ServerLevel parent, BlockPos pos) {
        Room room = roomOf(parent, pos, false);
        if (room == null) {
            return true;
        }
        for (BlockPos p : room.positions()) {
            if (!room.level.getBlockState(p).isAir()) {
                return false;
            }
        }
        return true;
    }

    /** The layer of blocks inside the room on the given side. */
    private static Iterable<BlockPos> innerFace(Room room, Direction dir) {
        BlockPos min = room.origin;
        BlockPos max = room.max();
        return switch (dir) {
            case DOWN -> BlockPos.betweenClosed(min, new BlockPos(max.getX(), min.getY(), max.getZ()));
            case UP -> BlockPos.betweenClosed(new BlockPos(min.getX(), max.getY(), min.getZ()), max);
            case NORTH -> BlockPos.betweenClosed(min, new BlockPos(max.getX(), max.getY(), min.getZ()));
            case SOUTH -> BlockPos.betweenClosed(new BlockPos(min.getX(), min.getY(), max.getZ()), max);
            case WEST -> BlockPos.betweenClosed(min, new BlockPos(min.getX(), max.getY(), max.getZ()));
            case EAST -> BlockPos.betweenClosed(new BlockPos(max.getX(), min.getY(), min.getZ()), max);
        };
    }

    /** The layer of blocks just outside the room on the given side. */
    private static Iterable<BlockPos> outerFace(Room room, Direction dir) {
        Room shifted = new Room(room.level, room.origin.relative(dir, RoomGeometry.ROOM));
        return innerFace(shifted, dir.getOpposite());
    }

    public static void setLoaded(Room room, boolean loaded) {
        TICKETS.forceChunk(room.level, room.origin,
                SectionPos.blockToSectionCoord(room.origin.getX()), SectionPos.blockToSectionCoord(room.origin.getZ()),
                loaded, true);
    }
}
