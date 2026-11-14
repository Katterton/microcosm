package dev.microcosm.device;

import com.mojang.serialization.MapCodec;
import dev.microcosm.block.CellBlock;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;

/** A manipulator. It mounts on any side of a cell and points its tool at it. */
public class ActuatorDeviceBlock extends DeviceBlock {
    public static final EnumProperty<Direction> FACING = BlockStateProperties.FACING;

    private final MapCodec<ActuatorDeviceBlock> codec;

    public ActuatorDeviceBlock(Properties properties, DeviceKind kind) {
        super(properties, kind);
        this.codec = simpleCodec(p -> new ActuatorDeviceBlock(p, kind));
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return codec;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    /** Points at a neighbouring cell if there is one, otherwise at the block it was placed against. */
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction facing = context.getClickedFace().getOpposite();
        for (Direction dir : Direction.values()) {
            if (context.getLevel().getBlockState(context.getClickedPos().relative(dir)).getBlock() instanceof CellBlock) {
                facing = dir;
                break;
            }
        }
        return defaultBlockState().setValue(FACING, facing);
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.setValue(FACING, mirror.mirror(state.getValue(FACING)));
    }
}
