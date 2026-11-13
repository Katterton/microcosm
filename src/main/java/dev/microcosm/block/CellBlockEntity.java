package dev.microcosm.block;

import dev.microcosm.ModRegistry;
import dev.microcosm.world.RoomManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

/**
 * Keeps the cell's room loaded while the cell is loaded, and remembers the port on each side of the room together
 * with the redstone signals crossing it.
 */
public class CellBlockEntity extends BlockEntity {
    private boolean ticketHeld;
    private final @Nullable BlockPos[] ports = new BlockPos[6];
    /** Signal arriving at each side from outside, for input ports. */
    private final int[] inputs = new int[6];
    /** Signal leaving each side, set by output ports. */
    private final int[] outputs = new int[6];

    public CellBlockEntity(BlockPos pos, BlockState state) {
        super(ModRegistry.CELL_BLOCK_ENTITY.get(), pos, state);
    }

    public @Nullable BlockPos port(Direction face) {
        return ports[face.get3DDataValue()];
    }

    public void setPort(Direction face, @Nullable BlockPos pos) {
        ports[face.get3DDataValue()] = pos == null ? null : pos.immutable();
        setChanged();
    }

    public int input(Direction face) {
        return inputs[face.get3DDataValue()];
    }

    /** @return whether the stored value changed */
    public boolean setInput(Direction face, int power) {
        int i = face.get3DDataValue();
        if (inputs[i] == power) {
            return false;
        }
        inputs[i] = power;
        setChanged();
        return true;
    }

    public int output(Direction face) {
        return outputs[face.get3DDataValue()];
    }

    public void setOutput(Direction face, int power) {
        outputs[face.get3DDataValue()] = power;
        setChanged();
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        for (Direction face : Direction.values()) {
            int i = face.get3DDataValue();
            ports[i] = input.getLong("port_" + face.getSerializedName()).map(BlockPos::of).orElse(null);
        }
        input.getIntArray("inputs").filter(a -> a.length == 6).ifPresent(a -> System.arraycopy(a, 0, inputs, 0, 6));
        input.getIntArray("outputs").filter(a -> a.length == 6).ifPresent(a -> System.arraycopy(a, 0, outputs, 0, 6));
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        for (Direction face : Direction.values()) {
            BlockPos pos = ports[face.get3DDataValue()];
            if (pos != null) {
                output.putLong("port_" + face.getSerializedName(), pos.asLong());
            }
        }
        output.putIntArray("inputs", inputs.clone());
        output.putIntArray("outputs", outputs.clone());
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level instanceof ServerLevel serverLevel) {
            RoomManager.Room room = RoomManager.roomOf(serverLevel, worldPosition, true);
            if (room != null) {
                RoomManager.setLoaded(room, true);
                ticketHeld = true;
            }
        }
    }

    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        super.preRemoveSideEffects(pos, state);
        if (level instanceof ServerLevel serverLevel) {
            RoomManager.onCellRemoved(serverLevel, pos);
        }
    }

    @Override
    public void onChunkUnloaded() {
        super.onChunkUnloaded();
        releaseTicket();
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        releaseTicket();
    }

    private void releaseTicket() {
        if (ticketHeld && level instanceof ServerLevel serverLevel) {
            RoomManager.Room room = RoomManager.roomOf(serverLevel, worldPosition, false);
            if (room != null) {
                RoomManager.setLoaded(room, false);
            }
            ticketHeld = false;
        }
    }
}
