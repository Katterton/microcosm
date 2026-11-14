package dev.microcosm.world;

import dev.microcosm.Microcosm;
import dev.microcosm.ModRegistry;
import dev.microcosm.block.CellBlock;
import dev.microcosm.block.CellBlockEntity;
import dev.microcosm.block.CellWallBlock;
import dev.microcosm.block.PortBlock;
import dev.microcosm.device.DeviceBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Every ordinary block is made of 16x16x16 microblocks of its own material. Viewing a block shows that interior
 * without changing anything; the first change (or entering it) divides the block into a micro block whose room is
 * filled with the material. A room that is uniform again (all one material, or empty) collapses back.
 */
public final class MicroBlocks {
    /** Blocks that cannot be divided even though they are full cubes. */
    public static final TagKey<Block> INDIVISIBLE = TagKey.create(Registries.BLOCK, Microcosm.id("indivisible"));
    /** A freshly divided block stays divided at least this long, so the action that divided it can happen. */
    public static final int COLLAPSE_GRACE_TICKS = 200;

    private MicroBlocks() {}

    public static boolean isMicro(BlockState state) {
        return state.getBlock() instanceof CellBlock;
    }

    /** Whether {@code state} can be divided into microblocks: a plain full cube without block entity. */
    public static boolean isDivisible(BlockState state) {
        return isDivisible(state, EmptyBlockGetter.INSTANCE, BlockPos.ZERO);
    }

    public static boolean isDivisible(BlockState state, BlockGetter level, BlockPos pos) {
        Block block = state.getBlock();
        return !state.isAir()
                && !(block instanceof CellBlock || block instanceof CellWallBlock || block instanceof PortBlock
                        || block instanceof DeviceBlock)
                && !state.hasBlockEntity()
                && state.getFluidState().isEmpty()
                && state.getDestroySpeed(level, pos) >= 0
                && state.isCollisionShapeFullBlock(level, pos)
                && !state.is(INDIVISIBLE);
    }

    /** Whether the block at {@code pos} is a micro block or could become one at this scale. */
    public static boolean canOpen(ServerLevel level, BlockPos pos) {
        if (MicrocosmLevels.depth(level) >= MicrocosmLevels.MAX_DEPTH || !level.isLoaded(pos)) {
            return false;
        }
        BlockState state = level.getBlockState(pos);
        return isMicro(state) || isDivisible(state, level, pos);
    }

    /**
     * The room of the block at {@code pos}, dividing it first if it is an ordinary block.
     *
     * @return null if the block is neither a micro block nor divisible
     */
    public static RoomManager.@Nullable Room open(ServerLevel level, BlockPos pos) {
        if (!canOpen(level, pos)) {
            return null;
        }
        BlockState state = level.getBlockState(pos);
        if (isMicro(state)) {
            return RoomManager.roomOf(level, pos, true);
        }
        RoomManager.Room room = RoomManager.roomOf(level, pos, true);
        if (room == null) {
            return null;
        }
        // Placing the micro block builds the room's walls; then the room is filled with the material.
        level.setBlock(pos, ModRegistry.CELL.get().defaultBlockState(), Block.UPDATE_ALL);
        for (BlockPos p : room.positions()) {
            room.level().setBlock(p.immutable(), state, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
        }
        if (level.getBlockEntity(pos) instanceof CellBlockEntity cell) {
            cell.holdOpen(level.getGameTime() + COLLAPSE_GRACE_TICKS);
        }
        return room;
    }

    /**
     * Turns a micro block back into an ordinary block (or air) if its room holds nothing but one divisible material
     * and nothing is moving around in it.
     *
     * @param states the room's block state ids in viewer order
     * @return whether it collapsed
     */
    public static boolean collapseIfUniform(ServerLevel level, BlockPos pos, int[] states) {
        int first = states[0];
        for (int id : states) {
            if (id != first) {
                return false;
            }
        }
        BlockState material = Block.stateById(first);
        if (!material.isAir() && !isDivisible(material)) {
            return false;
        }
        RoomManager.Room room = RoomManager.roomOf(level, pos, false);
        if (room == null) {
            return false;
        }
        AABB box = new AABB(Vec3.atLowerCornerOf(room.origin()), Vec3.atLowerCornerOf(room.max()).add(1, 1, 1));
        if (!room.level().getEntitiesOfClass(Entity.class, box).isEmpty()) {
            return false;
        }
        if (!material.isAir()) {
            for (BlockPos p : room.positions()) {
                room.level().setBlock(p.immutable(), Blocks.AIR.defaultBlockState(),
                        Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE | Block.UPDATE_SUPPRESS_DROPS);
            }
        }
        level.setBlock(pos, material, Block.UPDATE_ALL);
        return true;
    }
}
