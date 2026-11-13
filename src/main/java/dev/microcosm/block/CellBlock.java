package dev.microcosm.block;

import com.mojang.serialization.MapCodec;
import dev.microcosm.fabrication.FabricationHandler;
import dev.microcosm.world.PortLinks;
import dev.microcosm.world.RoomManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.BlockGetter;
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
 * A block containing a 16x16x16 room one scale level down. Ports inside the room connect its sides to the
 * matching sides of this block; a rising redstone signal on a side without a port checks the room against the cube
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

    /**
     * Sides with an input port forward their signal into the room. A rising signal on any side without a port
     * runs the cube recipe check.
     */
    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block block,
                                   @Nullable Orientation orientation, boolean movedByPiston) {
        if (!(level instanceof ServerLevel serverLevel) || !(level.getBlockEntity(pos) instanceof CellBlockEntity cell)) {
            return;
        }
        boolean triggered = false;
        for (Direction face : Direction.values()) {
            int signal = level.getSignal(pos.relative(face), face);
            PortLinks.PortRef port = PortLinks.portOn(serverLevel, pos, cell, face);
            if (port == null) {
                triggered |= signal > 0;
                continue;
            }
            // The block outside this side may have changed, so cached capabilities on the port are stale.
            port.level().invalidateCapabilities(port.pos());
            if (port.mode() == PortBlock.Mode.INPUT && cell.setInput(face, signal)) {
                port.level().scheduleTick(port.pos(), port.state().getBlock(), 1);
            }
        }
        if (triggered != state.getValue(POWERED)) {
            level.setBlock(pos, state.setValue(POWERED, triggered), Block.UPDATE_CLIENTS);
            if (triggered) {
                FabricationHandler.tryCube(serverLevel, pos);
            }
        }
    }

    @Override
    protected boolean isSignalSource(BlockState state) {
        return true;
    }

    /** Output ports drive the matching side of the cell. */
    @Override
    protected int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return level.getBlockEntity(pos) instanceof CellBlockEntity cell ? cell.output(direction.getOpposite()) : 0;
    }

    @Override
    protected int getDirectSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return getSignal(state, level, pos, direction);
    }
}
