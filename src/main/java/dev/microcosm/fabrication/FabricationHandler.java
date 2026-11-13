package dev.microcosm.fabrication;

import dev.microcosm.block.CellBlock;
import dev.microcosm.world.MicrocosmLevels;
import dev.microcosm.world.RoomManager;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import org.jspecify.annotations.Nullable;

/** Runs fabrication: local recipes on tool use, cube recipes on a redstone pulse into a cell. */
public final class FabricationHandler {
    /** Block updates for consumed recipe blocks: sync to clients, but no drops or container spills. */
    private static final int CONSUME_FLAGS = Block.UPDATE_ALL | Block.UPDATE_SKIP_BLOCK_ENTITY_SIDEEFFECTS;

    private FabricationHandler() {}

    /** A matched pattern: which recipe, in which rotation, at which minimum corner. */
    record Match(FabricationRecipes.Entry entry, Pattern pattern, BlockPos origin) {}

    // ---------------------------------------------------------------- local recipes

    /**
     * Using a recipe's tool on any block of a finished local structure crafts it. Works for real players and for
     * automation that uses items as a fake player (deployers and similar).
     */
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.getLevel() instanceof ServerLevel level
                && tryLocal(level, event.getPos(), event.getItemStack(), event.getEntity(), event.getHand())) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.SUCCESS);
        }
    }

    /**
     * Crafts the local recipe whose structure contains {@code clicked}, if {@code tool} is its tool.
     *
     * @return whether a recipe was crafted
     */
    public static boolean tryLocal(ServerLevel level, BlockPos clicked, ItemStack tool, @Nullable Player player, InteractionHand hand) {
        if (tool.isEmpty()) {
            return false;
        }
        Match match = findLocal(level, clicked, tool);
        if (match == null) {
            return false;
        }
        consume(level, match.pattern, match.origin);
        Block.popResource(level, clicked, match.entry.recipe.result().create());
        if (tool.isDamageableItem()) {
            int damage = match.entry.recipe.toolDamage();
            if (player != null) {
                tool.hurtAndBreak(damage, player, hand);
            } else {
                tool.hurtAndBreak(damage, level, (LivingEntity) null, item -> {});
            }
        }
        return true;
    }

    static @Nullable Match findLocal(ServerLevel level, BlockPos clicked, ItemStack tool) {
        int depth = MicrocosmLevels.depth(level);
        BlockState clickedState = level.getBlockState(clicked);
        for (FabricationRecipes.Entry entry : FabricationRecipes.all()) {
            FabricationRecipe recipe = entry.recipe;
            if (recipe.mode() != FabricationRecipe.Mode.LOCAL || !recipe.allowsScale(depth)
                    || !recipe.tool().orElseThrow().test(tool)) {
                continue;
            }
            Pattern[] rotations = entry.rotations(level.getServer());
            if (rotations == null) {
                continue;
            }
            for (Pattern pattern : rotations) {
                BlockPos origin = findAnchor(level, pattern, clicked, clickedState);
                if (origin != null) {
                    return new Match(entry, pattern, origin);
                }
            }
        }
        return null;
    }

    /** Tries every block cell of the pattern as the clicked block and returns the matching minimum corner. */
    private static @Nullable BlockPos findAnchor(ServerLevel level, Pattern pattern, BlockPos clicked, BlockState clickedState) {
        for (int y = 0; y < pattern.sizeY; y++) {
            for (int z = 0; z < pattern.sizeZ; z++) {
                for (int x = 0; x < pattern.sizeX; x++) {
                    if (pattern.get(x, y, z) instanceof Pattern.Blocks cell && cell.matches(clickedState)) {
                        BlockPos origin = clicked.offset(-x, -y, -z);
                        if (pattern.matches(level::getBlockState, origin)) {
                            return origin;
                        }
                    }
                }
            }
        }
        return null;
    }

    // ---------------------------------------------------------------- cube recipes

    /**
     * Checks the room of the cell at {@code cellPos} against all cube recipes. On a match the recipe blocks are
     * consumed, anything else left in the room drops next to the cell, and the cell turns into the result.
     *
     * @return whether a recipe was crafted
     */
    public static boolean tryCube(ServerLevel parent, BlockPos cellPos) {
        RoomManager.Room room = RoomManager.roomOf(parent, cellPos, false);
        if (room == null) {
            return false;
        }
        Match match = findCube(room);
        if (match == null) {
            return false;
        }
        // Never destroy a nested cell whose own room still has something in it.
        for (BlockPos p : room.positions()) {
            BlockState state = room.level().getBlockState(p);
            if (state.getBlock() instanceof CellBlock && !RoomManager.isRoomEmpty(room.level(), p)) {
                return false;
            }
        }
        consume(room.level(), match.pattern, match.origin);
        for (BlockPos p : room.positions()) {
            BlockState state = room.level().getBlockState(p);
            if (!state.isAir()) {
                BlockEntity be = room.level().getBlockEntity(p);
                for (ItemStack drop : Block.getDrops(state, room.level(), p, be)) {
                    Block.popResource(parent, cellPos, drop);
                }
                room.level().setBlock(p.immutable(), Blocks.AIR.defaultBlockState(), CONSUME_FLAGS);
            }
        }
        ItemStack result = match.entry.recipe.result().create();
        if (result.getItem() instanceof BlockItem blockItem) {
            parent.setBlock(cellPos, blockItem.getBlock().defaultBlockState(), Block.UPDATE_ALL);
            result.shrink(1);
        } else {
            parent.setBlock(cellPos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        }
        if (!result.isEmpty()) {
            Block.popResource(parent, cellPos, result);
        }
        return true;
    }

    static @Nullable Match findCube(RoomManager.Room room) {
        int depth = MicrocosmLevels.depth(room.level());
        for (FabricationRecipes.Entry entry : FabricationRecipes.all()) {
            if (entry.recipe.mode() != FabricationRecipe.Mode.CUBE || !entry.recipe.allowsScale(depth)) {
                continue;
            }
            Pattern[] rotations = entry.rotations(room.level().getServer());
            if (rotations == null) {
                continue;
            }
            for (Pattern pattern : rotations) {
                if (pattern.matches(room.level()::getBlockState, room.origin())) {
                    return new Match(entry, pattern, room.origin());
                }
            }
        }
        return null;
    }

    /** Removes every block the pattern asked for. Cells marked as "anything" are left alone. */
    private static void consume(ServerLevel level, Pattern pattern, BlockPos origin) {
        List<BlockPos> remove = new ArrayList<>();
        for (int y = 0; y < pattern.sizeY; y++) {
            for (int z = 0; z < pattern.sizeZ; z++) {
                for (int x = 0; x < pattern.sizeX; x++) {
                    if (pattern.get(x, y, z) instanceof Pattern.Blocks) {
                        remove.add(origin.offset(x, y, z));
                    }
                }
            }
        }
        for (BlockPos p : remove) {
            level.setBlock(p, Blocks.AIR.defaultBlockState(), CONSUME_FLAGS);
        }
    }
}
