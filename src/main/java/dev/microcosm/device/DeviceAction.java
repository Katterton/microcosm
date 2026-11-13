package dev.microcosm.device;

import dev.microcosm.block.CellBlock;
import dev.microcosm.fabrication.FabricationHandler;
import dev.microcosm.world.MicrocosmLevels;
import dev.microcosm.world.RoomManager;
import dev.microcosm.world.ShiftManager;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;

/**
 * One queued manipulation of a room block.
 *
 * @param path  cells from the one next to the device down to the one whose room is targeted
 * @param local position inside that room, 0..15 on each axis
 * @param slot  inventory slot of the actuator to place or use from
 */
public record DeviceAction(List<BlockPos> path, BlockPos local, Type type, int slot) {
    public enum Type {
        PLACE,
        BREAK,
        USE
    }

    /** Outcome shown in the viewer's status line; the name is the translation key suffix. */
    public enum Result {
        DONE,
        ROOM_GONE,
        TOO_DEEP,
        NOTHING_TO_PLACE,
        BLOCKED,
        UNBREAKABLE,
        NO_EFFECT,
        NO_ENERGY,
        QUEUE_FULL,
        NO_ACTUATOR,
        QUEUED
    }

    Result execute(ServerLevel deviceLevel, DeviceBlockEntity actuator) {
        RoomManager.Room room = RoomPath.resolve(deviceLevel, path);
        if (room == null || !RoomPath.isLocal(local)) {
            return Result.ROOM_GONE;
        }
        if (MicrocosmLevels.depth(room.level()) > actuator.kind().actuatorTier) {
            return Result.TOO_DEEP;
        }
        ServerLevel level = room.level();
        BlockPos target = room.origin().offset(local);
        FakePlayer player = FakePlayerFactory.getMinecraft(level);
        player.setPos(Vec3.atCenterOf(target.above()));
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(target), Direction.UP, target, false);
        return switch (type) {
            case BREAK -> breakBlock(level, target, actuator);
            case PLACE -> {
                ItemStack stack = actuator.getItem(slot);
                if (stack.isEmpty()) {
                    yield Result.NOTHING_TO_PLACE;
                }
                if (!(stack.getItem() instanceof BlockItem blockItem)) {
                    yield use(level, target, actuator, player, hit);
                }
                if (!level.getBlockState(target).canBeReplaced()) {
                    yield Result.BLOCKED;
                }
                player.setItemInHand(InteractionHand.MAIN_HAND, stack);
                InteractionResult result = blockItem.place(
                        new BlockPlaceContext(level, player, InteractionHand.MAIN_HAND, stack, hit));
                takeBack(actuator, player);
                yield result.consumesAction() ? Result.DONE : Result.BLOCKED;
            }
            case USE -> use(level, target, actuator, player, hit);
        };
    }

    private Result use(ServerLevel level, BlockPos target, DeviceBlockEntity actuator, FakePlayer player, BlockHitResult hit) {
        ItemStack stack = actuator.getItem(slot);
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        try {
            if (!stack.isEmpty() && FabricationHandler.tryLocal(level, target, stack, player, InteractionHand.MAIN_HAND)) {
                return Result.DONE;
            }
            BlockState state = level.getBlockState(target);
            InteractionResult result = stack.isEmpty()
                    ? state.useWithoutItem(level, player, hit)
                    : state.useItemOn(stack, level, player, InteractionHand.MAIN_HAND, hit);
            if (!result.consumesAction() && !stack.isEmpty()) {
                result = stack.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, hit));
            }
            return result.consumesAction() ? Result.DONE : Result.NO_EFFECT;
        } finally {
            takeBack(actuator, player);
        }
    }

    /** Whatever is left in the fake player's hand goes back into the slot it came from. */
    private void takeBack(DeviceBlockEntity actuator, FakePlayer player) {
        actuator.setItem(slot, player.getMainHandItem());
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
    }

    private static Result breakBlock(ServerLevel level, BlockPos target, DeviceBlockEntity actuator) {
        BlockState state = level.getBlockState(target);
        if (state.isAir()) {
            return Result.NO_EFFECT;
        }
        if (state.getDestroySpeed(level, target) < 0
                || (state.getBlock() instanceof CellBlock && (!RoomManager.isRoomEmpty(level, target)
                        || ShiftManager.hasPlayersInside(level, target)))) {
            return Result.UNBREAKABLE;
        }
        BlockEntity blockEntity = level.getBlockEntity(target);
        for (ItemStack drop : Block.getDrops(state, level, target, blockEntity)) {
            ItemStack rest = actuator.insert(drop);
            if (!rest.isEmpty()) {
                Block.popResource(level, target, rest);
            }
        }
        level.destroyBlock(target, false);
        return Result.DONE;
    }
}
