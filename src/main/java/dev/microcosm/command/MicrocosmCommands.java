package dev.microcosm.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.microcosm.block.CellBlock;
import dev.microcosm.fabrication.FabricationHandler;
import dev.microcosm.fabrication.FabricationRecipes;
import dev.microcosm.world.MicrocosmLevels;
import dev.microcosm.world.RoomManager;
import java.util.Set;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Debug commands until the microscope and manipulator exist.
 * <ul>
 *   <li>{@code /microcosm enter [pos]} - teleport into the room of the cell you look at (or at pos)</li>
 *   <li>{@code /microcosm exit} - leave the room you are in, back on top of its cell</li>
 *   <li>{@code /microcosm check [pos]} - run the cube recipe check, same as a redstone pulse</li>
 *   <li>{@code /microcosm info} - where you are</li>
 * </ul>
 */
public final class MicrocosmCommands {
    private MicrocosmCommands() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("microcosm")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(Commands.literal("enter")
                        .executes(c -> enter(c.getSource(), lookedAtCell(c.getSource())))
                        .then(Commands.argument("pos", BlockPosArgument.blockPos())
                                .executes(c -> enter(c.getSource(), BlockPosArgument.getLoadedBlockPos(c, "pos")))))
                .then(Commands.literal("exit").executes(c -> exit(c.getSource())))
                .then(Commands.literal("check")
                        .executes(c -> check(c.getSource(), lookedAtCell(c.getSource())))
                        .then(Commands.argument("pos", BlockPosArgument.blockPos())
                                .executes(c -> check(c.getSource(), BlockPosArgument.getLoadedBlockPos(c, "pos")))))
                .then(Commands.literal("info").executes(c -> info(c.getSource()))));
    }

    private static BlockPos lookedAtCell(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        HitResult hit = player.pick(8.0, 1.0f, false);
        if (hit instanceof BlockHitResult blockHit && hit.getType() == HitResult.Type.BLOCK) {
            return blockHit.getBlockPos();
        }
        return player.blockPosition().below();
    }

    private static int enter(CommandSourceStack source, BlockPos pos) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        ServerLevel level = source.getLevel();
        if (!(level.getBlockState(pos).getBlock() instanceof CellBlock)) {
            source.sendFailure(Component.translatable("commands.microcosm.not_a_cell"));
            return 0;
        }
        RoomManager.Room room = RoomManager.roomOf(level, pos, true);
        if (room == null) {
            source.sendFailure(Component.translatable("commands.microcosm.deepest_level"));
            return 0;
        }
        Vec3 target = Vec3.atBottomCenterOf(room.spawnPoint());
        player.teleport(new TeleportTransition(room.level(), target, Vec3.ZERO, player.getYRot(), player.getXRot(),
                Set.of(), TeleportTransition.DO_NOTHING));
        source.sendSuccess(() -> Component.translatable("commands.microcosm.entered",
                MicrocosmLevels.depth(room.level())), false);
        return 1;
    }

    private static int exit(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        RoomManager.Parent parent = RoomManager.parentOf(source.getLevel(), player.blockPosition());
        if (parent == null) {
            source.sendFailure(Component.translatable("commands.microcosm.not_in_room"));
            return 0;
        }
        Vec3 target = Vec3.atBottomCenterOf(parent.pos().above());
        player.teleport(new TeleportTransition(parent.level(), target, Vec3.ZERO, player.getYRot(), player.getXRot(),
                Set.of(), TeleportTransition.DO_NOTHING));
        return 1;
    }

    private static int check(CommandSourceStack source, BlockPos pos) {
        ServerLevel level = source.getLevel();
        if (!(level.getBlockState(pos).getBlock() instanceof CellBlock)) {
            source.sendFailure(Component.translatable("commands.microcosm.not_a_cell"));
            return 0;
        }
        if (FabricationHandler.tryCube(level, pos)) {
            source.sendSuccess(() -> Component.translatable("commands.microcosm.crafted"), true);
            return 1;
        }
        source.sendFailure(Component.translatable("commands.microcosm.no_match"));
        return 0;
    }

    private static int info(CommandSourceStack source) {
        ServerLevel level = source.getLevel();
        BlockPos pos = BlockPos.containing(source.getPosition());
        int depth = MicrocosmLevels.depth(level);
        RoomManager.Parent parent = RoomManager.parentOf(level, pos);
        source.sendSuccess(() -> Component.translatable("commands.microcosm.info", depth,
                parent == null ? "-" : parent.level().dimension().identifier() + " " + parent.pos().toShortString(),
                FabricationRecipes.all().size()), false);
        return 1;
    }
}
