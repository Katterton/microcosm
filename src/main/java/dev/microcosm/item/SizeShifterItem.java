package dev.microcosm.item;

import dev.microcosm.MicrocosmConfig;
import dev.microcosm.block.CellBlock;
import dev.microcosm.world.MicrocosmLevels;
import dev.microcosm.world.ShiftManager;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.common.util.FakePlayer;
import org.jspecify.annotations.Nullable;

/**
 * Lets a player enter rooms in person. Use it on a cell to shrink into its room; sneak-use it inside a room to grow
 * back out next to the cell.
 */
public class SizeShifterItem extends Item {
    public SizeShifterItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        if (!(context.getLevel().getBlockState(context.getClickedPos()).getBlock() instanceof CellBlock)) {
            return InteractionResult.PASS;
        }
        if (context.getLevel() instanceof ServerLevel level && context.getPlayer() instanceof ServerPlayer player
                && !(player instanceof FakePlayer)) {
            finish(player, context.getItemInHand(), context.getHand(), ShiftManager.enter(player, level, context.getClickedPos()));
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (!player.isShiftKeyDown() || MicrocosmLevels.depth(level) == 0) {
            return InteractionResult.PASS;
        }
        if (level instanceof ServerLevel serverLevel && player instanceof ServerPlayer serverPlayer
                && !(player instanceof FakePlayer)) {
            finish(serverPlayer, player.getItemInHand(hand), hand, ShiftManager.exit(serverPlayer, serverLevel));
        }
        return InteractionResult.SUCCESS;
    }

    private static void finish(ServerPlayer player, ItemStack stack, InteractionHand hand, @Nullable String error) {
        if (error != null) {
            player.sendOverlayMessage(Component.translatable(error));
            return;
        }
        player.getCooldowns().addCooldown(stack, MicrocosmConfig.ENTERING_COOLDOWN.getAsInt());
        stack.hurtAndBreak(1, player, hand);
    }
}
