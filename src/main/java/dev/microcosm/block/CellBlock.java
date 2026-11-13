package dev.microcosm.block;

import com.mojang.serialization.MapCodec;
import dev.microcosm.fabrication.FabricationHandler;
import dev.microcosm.world.RoomManager;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.redstone.Orientation;
import org.jspecify.annotations.Nullable;

/**
 * A block containing a 16x16x16 room one scale level down. A rising redstone edge checks the room against the cube
 * fabrication recipes.
 */
public class CellBlock extends Block implements EntityBlock {
    public static final MapCodec<CellBlock> CODEC = simpleCodec(CellBlock::new);
    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;

    public CellBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(POWERED, false));
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(POWERED);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new CellBlockEntity(pos, state);
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        if (oldState.getBlock() != this && level instanceof ServerLevel serverLevel) {
            RoomManager.onCellPlaced(serverLevel, pos);
        }
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block block,
                                   @Nullable Orientation orientation, boolean movedByPiston) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        boolean powered = level.hasNeighborSignal(pos);
        if (powered != state.getValue(POWERED)) {
            level.setBlock(pos, state.setValue(POWERED, powered), Block.UPDATE_CLIENTS);
            if (powered) {
                FabricationHandler.tryCube(serverLevel, pos);
            }
        }
    }
}
