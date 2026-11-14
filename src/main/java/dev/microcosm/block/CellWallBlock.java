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
 * The boundary of a room, built like its cell seen from inside: every room side is clear glass, and only the wall
 * blocks along the room's edges (fewer than four wall neighbours) form a solid frame. Each wall block keeps track of
 * which neighbours are walls, so the frame follows the room shape (connected textures).
 */
public class CellWallBlock extends Block {
    public static final MapCodec<CellWallBlock> CODEC = simpleCodec(CellWallBlock::new);
    private static final Map<Direction, BooleanProperty> CONNECTED = PipeBlock.PROPERTY_BY_DIRECTION;
    /** Whether this block is part of the frame along a room edge (otherwise it is clear glass). */
    public static final BooleanProperty FRAME = BooleanProperty.create("frame");

    public CellWallBlock(Properties properties) {
        super(properties);
        BlockState state = stateDefinition.any().setValue(FRAME, true);
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
        builder.add(FRAME);
    }

    /** The wall state for {@code pos}, connected to the walls already around it. */
    public BlockState connectedState(BlockGetter level, BlockPos pos) {
        BlockState state = defaultBlockState();
        for (Map.Entry<Direction, BooleanProperty> entry : CONNECTED.entrySet()) {
            state = state.setValue(entry.getValue(), level.getBlockState(pos.relative(entry.getKey())).getBlock() == this);
        }
        return withFrame(state);
    }

    /** Inside a pane a wall block has four wall neighbours; fewer means it lies on an edge of the room. */
    private static BlockState withFrame(BlockState state) {
        int connections = 0;
        for (BooleanProperty property : CONNECTED.values()) {
            if (state.getValue(property)) {
                connections++;
            }
        }
        return state.setValue(FRAME, connections < 4);
    }

    @Override
    protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos,
                                     Direction directionToNeighbour, BlockPos neighbourPos, BlockState neighbourState,
                                     RandomSource random) {
        return withFrame(state.setValue(CONNECTED.get(directionToNeighbour), neighbourState.getBlock() == this));
    }
}
