package dev.microcosm.world;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.microcosm.Microcosm;
import dev.microcosm.ModRegistry;
import dev.microcosm.block.CellBlock;
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
import net.neoforged.neoforge.common.world.chunk.TicketController;
import org.jspecify.annotations.Nullable;

/**
 * Allocates regions in the microcosm levels, keeps room walls in sync with which cells exist, and keeps rooms
 * loaded while their cell is loaded.
 */
public final class RoomManager extends SavedData {
    /**
     * Forces the chunk of each active room. Tickets are dropped on server start and re-added by cell block entities
     * as they load, so a room is only loaded while its parent cell is (which chains through all nested levels).
     */
    public static final TicketController TICKETS = new TicketController(Microcosm.id("rooms"), (level, helper) ->
            new ArrayList<>(helper.getBlockTickets().keySet()).forEach(helper::removeAllTickets));

    private record Region(Identifier parentDimension, int sectionX, int sectionY, int sectionZ, int childDepth, int index) {
        static final Codec<Region> CODEC = RecordCodecBuilder.create(i -> i.group(
                Identifier.CODEC.fieldOf("parent").forGetter(Region::parentDimension),
                Codec.INT.fieldOf("sx").forGetter(Region::sectionX),
                Codec.INT.fieldOf("sy").forGetter(Region::sectionY),
                Codec.INT.fieldOf("sz").forGetter(Region::sectionZ),
                Codec.INT.fieldOf("depth").forGetter(Region::childDepth),
                Codec.INT.fieldOf("index").forGetter(Region::index)
        ).apply(i, Region::new));

        String key() {
            return key(parentDimension, sectionX, sectionY, sectionZ);
        }

        static String key(Identifier dim, int sx, int sy, int sz) {
            return dim + "|" + sx + "|" + sy + "|" + sz;
        }
    }

    private static final Codec<RoomManager> CODEC = Region.CODEC.listOf()
            .xmap(RoomManager::new, m -> m.regions).fieldOf("regions").codec();

    private static final SavedDataType<RoomManager> TYPE =
            new SavedDataType<>(Microcosm.id("rooms"), RoomManager::new, CODEC);

    private final List<Region> regions = new ArrayList<>();
    private final Map<String, Region> byParentSection = new HashMap<>();
    private final Map<Long, Region> byChildIndex = new HashMap<>();
    private final int[] nextIndex = new int[MicrocosmLevels.MAX_DEPTH + 1];

    private RoomManager() {}

    private RoomManager(List<Region> loaded) {
        loaded.forEach(this::index);
    }

    private void index(Region region) {
        regions.add(region);
        byParentSection.put(region.key(), region);
        byChildIndex.put(childKey(region.childDepth, region.index), region);
        nextIndex[region.childDepth] = Math.max(nextIndex[region.childDepth], region.index + 1);
    }

    private static long childKey(int depth, int index) {
        return ((long) depth << 32) | (index & 0xFFFFFFFFL);
    }

    public static RoomManager get(MinecraftServer server) {
        return server.getDataStorage().computeIfAbsent(TYPE);
    }

    /** A cell's room: the level it is in and the minimum corner of its 16x16x16 space. */
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

    /** Where a room's cell is, one level up. */
    public record Parent(ServerLevel level, BlockPos pos) {}

    /** The room of the cell at {@code pos}, or null at the deepest level or if none was allocated yet. */
    public static @Nullable Room roomOf(ServerLevel parent, BlockPos pos, boolean allocate) {
        ServerLevel child = MicrocosmLevels.childOf(parent);
        if (child == null) {
            return null;
        }
        RoomManager manager = get(parent.getServer());
        Identifier dim = parent.dimension().identifier();
        int sx = SectionPos.blockToSectionCoord(pos.getX());
        int sy = SectionPos.blockToSectionCoord(pos.getY());
        int sz = SectionPos.blockToSectionCoord(pos.getZ());
        Region region = manager.byParentSection.get(Region.key(dim, sx, sy, sz));
        if (region == null) {
            if (!allocate) {
                return null;
            }
            int depth = MicrocosmLevels.depth(child);
            region = new Region(dim, sx, sy, sz, depth, manager.nextIndex[depth]);
            manager.index(region);
            manager.setDirty();
        }
        return new Room(child, RoomGeometry.roomOrigin(region.index, pos));
    }

    /** The cell that owns the room containing {@code pos}, if any. */
    public static @Nullable Parent parentOf(ServerLevel child, BlockPos pos) {
        int depth = MicrocosmLevels.depth(child);
        RoomGeometry.Slot slot = RoomGeometry.slotAt(pos);
        if (depth == 0 || slot == null) {
            return null;
        }
        Region region = get(child.getServer()).byChildIndex.get(childKey(depth, slot.regionIndex()));
        if (region == null) {
            return null;
        }
        ServerLevel parent = child.getServer().getLevel(ResourceKey.create(Registries.DIMENSION, region.parentDimension));
        if (parent == null) {
            return null;
        }
        BlockPos cellPos = new BlockPos(
                SectionPos.sectionToBlockCoord(region.sectionX) + slot.x(),
                SectionPos.sectionToBlockCoord(region.sectionY) + slot.y(),
                SectionPos.sectionToBlockCoord(region.sectionZ) + slot.z());
        return new Parent(parent, cellPos);
    }

    private static boolean isCell(Level level, BlockPos pos) {
        return level.getBlockState(pos).getBlock() instanceof CellBlock;
    }

    /** Neighbouring cells in the same section have rooms that touch, so the wall between them is left open. */
    private static boolean connected(Level parent, BlockPos pos, Direction dir) {
        BlockPos neighbor = pos.relative(dir);
        return RoomGeometry.sameSection(pos, neighbor) && isCell(parent, neighbor);
    }

    public static void onCellPlaced(ServerLevel parent, BlockPos pos) {
        Room room = roomOf(parent, pos, true);
        if (room == null) {
            return;
        }
        BlockState wall = ModRegistry.CELL_WALL.get().defaultBlockState();
        // Walls that used to separate this (inactive) slot from active neighbours sit on its inner faces.
        for (Direction dir : Direction.values()) {
            for (BlockPos p : innerFace(room, dir)) {
                if (room.level.getBlockState(p).is(ModRegistry.CELL_WALL.get())) {
                    room.level.setBlock(p, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
                }
            }
        }
        // Wall off every side that does not lead into a neighbouring room.
        for (Direction dir : Direction.values()) {
            if (!connected(parent, pos, dir)) {
                for (BlockPos p : outerFace(room, dir)) {
                    room.level.setBlock(p, wall, Block.UPDATE_CLIENTS);
                }
            }
        }
    }

    public static void onCellRemoved(ServerLevel parent, BlockPos pos) {
        Room room = roomOf(parent, pos, false);
        if (room == null) {
            return;
        }
        BlockState wall = ModRegistry.CELL_WALL.get().defaultBlockState();
        // Close the openings into rooms of neighbouring cells that still exist.
        for (Direction dir : Direction.values()) {
            if (connected(parent, pos, dir)) {
                for (BlockPos p : innerFace(room, dir)) {
                    room.level.setBlock(p, wall, Block.UPDATE_CLIENTS);
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
