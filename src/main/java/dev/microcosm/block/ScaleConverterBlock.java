package dev.microcosm.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.jspecify.annotations.Nullable;

/**
 * Converts material between scales: one block becomes 4096 blocks of the next smaller scale, and 4096 become one
 * of the next larger scale. Items go in from the top or sides and come out at the bottom (hoppers work). By hand:
 * right-click with an item to insert it, with an empty hand to take the output, sneak-right-click to switch mode.
 */
public class ScaleConverterBlock extends Block implements EntityBlock {
    public static final MapCodec<ScaleConverterBlock> CODEC = simpleCodec(ScaleConverterBlock::new);
    public static final EnumProperty<Mode> MODE = EnumProperty.create("mode", Mode.class);

    public enum Mode implements StringRepresentable {
        SHRINK("shrink"),
        GROW("grow");

        private final String name;

        Mode(String name) {
            this.name = name;
        }

        @Override
        public String getSerializedName() {
            return name;
        }
    }

    public ScaleConverterBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(MODE, Mode.SHRINK));
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(MODE);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ScaleConverterBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide() ? null : (l, pos, s, be) -> {
            if (be instanceof ScaleConverterBlockEntity converter && l instanceof ServerLevel serverLevel) {
                converter.serverTick(serverLevel, s.getValue(MODE));
            }
        };
    }

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                          InteractionHand hand, BlockHitResult hit) {
        if (player.isShiftKeyDown()) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof ScaleConverterBlockEntity converter) {
            converter.insertByHand(stack);
            converter.showStatus(player, state.getValue(MODE));
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.isClientSide() || !(level.getBlockEntity(pos) instanceof ScaleConverterBlockEntity converter)) {
            return InteractionResult.SUCCESS;
        }
        Mode mode = state.getValue(MODE);
        if (player.isShiftKeyDown()) {
            mode = mode == Mode.SHRINK ? Mode.GROW : Mode.SHRINK;
            level.setBlock(pos, state.setValue(MODE, mode), Block.UPDATE_ALL);
        } else {
            converter.takeOutput(player);
        }
        converter.showStatus(player, mode);
        return InteractionResult.SUCCESS;
    }
}
