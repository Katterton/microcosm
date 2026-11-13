package dev.microcosm.block;

import com.mojang.serialization.MapCodec;
import dev.microcosm.world.PortLinks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.redstone.Orientation;
import net.minecraft.world.phys.BlockHitResult;
import org.jspecify.annotations.Nullable;

/**
 * Connects one side of a room with the same side of its cell, one level up.
 *
 * <p>Placed against a room wall, it faces that wall. Capabilities (items, fluids, energy, ...) pass through in both
 * directions: the block behind the port inside the room is reachable from outside the cell's face, and the block
 * outside the cell's face is reachable through the port from inside. Redstone passes in one direction, set by
 * right-clicking: {@code input} carries the signal from outside into the room, {@code output} the other way. Each
 * crossing takes one tick. Only one port per room side is active.</p>
 */
public class PortBlock extends Block {
    public static final MapCodec<PortBlock> CODEC = simpleCodec(PortBlock::new);
    public static final EnumProperty<Direction> FACING = BlockStateProperties.FACING;
    public static final EnumProperty<Mode> MODE = EnumProperty.create("mode", Mode.class);
    public static final IntegerProperty POWER = BlockStateProperties.POWER;

    public enum Mode implements StringRepresentable {
        INPUT("input"),
        OUTPUT("output");

        private final String name;

        Mode(String name) {
            this.name = name;
        }

        @Override
        public String getSerializedName() {
            return name;
        }
    }

    public PortBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any()
                .setValue(FACING, Direction.NORTH).setValue(MODE, Mode.INPUT).setValue(POWER, 0));
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, MODE, POWER);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getClickedFace().getOpposite());
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        if (oldState.getBlock() == this || !(level instanceof ServerLevel serverLevel)) {
            return;
        }
        PortLinks.Link link = PortLinks.link(serverLevel, pos, state.getValue(FACING));
        if (link == null) {
            return;
        }
        PortLinks.PortRef existing = PortLinks.portOn(link.parent(), link.cellPos(), link.cell(), link.face());
        if (existing != null && !existing.pos().equals(pos)) {
            // Only one port per side: the newer one pops off.
            level.destroyBlock(pos, true);
            return;
        }
        link.cell().setPort(link.face(), pos);
        link.parent().invalidateCapabilities(link.cellPos());
        level.scheduleTick(pos, this, 1);
    }

    @Override
    protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean movedByPiston) {
        PortLinks.Link link = PortLinks.link(level, pos, state.getValue(FACING));
        if (link != null && pos.equals(link.cell().port(link.face()))) {
            link.cell().setPort(link.face(), null);
            link.cell().setInput(link.face(), 0);
            setCellOutput(link, 0);
            link.parent().invalidateCapabilities(link.cellPos());
        }
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block block,
                                   @Nullable Orientation orientation, boolean movedByPiston) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        PortLinks.Link link = PortLinks.link(serverLevel, pos, state.getValue(FACING));
        if (link != null) {
            // The block behind the port may have changed, so cached capabilities on the cell are stale.
            link.parent().invalidateCapabilities(link.cellPos());
        }
        if (state.getValue(MODE) == Mode.OUTPUT && !level.getBlockTicks().hasScheduledTick(pos, this)) {
            level.scheduleTick(pos, this, 1);
        }
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return InteractionResult.SUCCESS;
        }
        Mode mode = state.getValue(MODE) == Mode.INPUT ? Mode.OUTPUT : Mode.INPUT;
        level.setBlock(pos, state.setValue(MODE, mode).setValue(POWER, 0), Block.UPDATE_ALL);
        PortLinks.Link link = PortLinks.link(serverLevel, pos, state.getValue(FACING));
        if (link != null) {
            setCellOutput(link, 0);
        }
        level.scheduleTick(pos, this, 1);
        player.sendOverlayMessage(Component.translatable("message.microcosm.port_mode." + mode.getSerializedName()));
        return InteractionResult.SUCCESS;
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        Direction facing = state.getValue(FACING);
        PortLinks.Link link = PortLinks.link(level, pos, facing);
        int power;
        if (link == null) {
            power = 0;
        } else if (state.getValue(MODE) == Mode.INPUT) {
            power = link.cell().input(facing);
        } else {
            power = level.getSignal(pos.relative(facing.getOpposite()), facing.getOpposite());
            setCellOutput(link, power);
        }
        if (power != state.getValue(POWER)) {
            // Output ports do not emit inside the room, so they need no neighbour updates.
            int flags = state.getValue(MODE) == Mode.INPUT ? Block.UPDATE_ALL : Block.UPDATE_CLIENTS;
            level.setBlock(pos, state.setValue(POWER, power), flags);
        }
    }

    private static void setCellOutput(PortLinks.Link link, int power) {
        if (link.cell().output(link.face()) != power) {
            link.cell().setOutput(link.face(), power);
            Block cellBlock = link.parent().getBlockState(link.cellPos()).getBlock();
            link.parent().updateNeighborsAt(link.cellPos(), cellBlock, null);
            link.parent().updateNeighborsAt(link.cellPos().relative(link.face()), cellBlock, null);
        }
    }

    @Override
    protected boolean isSignalSource(BlockState state) {
        return state.getValue(MODE) == Mode.INPUT;
    }

    @Override
    protected int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return state.getValue(MODE) == Mode.INPUT && direction == state.getValue(FACING) ? state.getValue(POWER) : 0;
    }

    @Override
    protected int getDirectSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return getSignal(state, level, pos, direction);
    }
}
