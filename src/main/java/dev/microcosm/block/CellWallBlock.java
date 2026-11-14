package dev.microcosm.block;

import com.mojang.serialization.MapCodec;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.PipeBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

/**
 * The see-through boundary of a room. Each wall block knows which neighbours are walls too, so its model only draws
 * frame lines where the glass surface ends: every room side looks like one big pane (connected textures).
 */
public class CellWallBlock extends Block {
    public static final MapCodec<CellWallBlock> CODEC = simpleCodec(CellWallBlock::new);
    private static final Map<Direction, BooleanProperty> CONNECTED = PipeBlock.PROPERTY_BY_DIRECTION;

    public CellWallBlock(Properties properties) {
        super(properties);
        BlockState state = stateDefinition.any();
        for (BooleanProperty property : CONNECTED.values()) {
            state = state.setValue(property, false);
        }
        registerDefaultState(state);
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        CONNECTED.values().forEach(builder::add);
    }

    /** The wall state for {@code pos}, connected to the walls already around it. */
    public BlockState connectedState(BlockGetter level, BlockPos pos) {
        BlockState state = defaultBlockState();
        for (Map.Entry<Direction, BooleanProperty> entry : CONNECTED.entrySet()) {
            state = state.setValue(entry.getValue(), level.getBlockState(pos.relative(entry.getKey())).getBlock() == this);
        }
        return state;
    }

    @Override
    protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos,
                                     Direction directionToNeighbour, BlockPos neighbourPos, BlockState neighbourState,
                                     RandomSource random) {
        return state.setValue(CONNECTED.get(directionToNeighbour), neighbourState.getBlock() == this);
    }
}
