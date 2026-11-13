package dev.microcosm.block;

import dev.microcosm.MicrocosmConfig;
import dev.microcosm.ModRegistry;
import dev.microcosm.device.ViewerSessions;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.Block;
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

    /**
     * Miniature of the room: block state ids in {@code (y * 16 + z) * 16 + x} order. On the server the last state sent
     * to clients, on the client the one to render. Null when unknown.
     */
    private int @Nullable [] preview;
    private int previewHash;
    /** Bumped whenever the client receives a new preview, so renderers can rebuild their mesh. */
    private int previewVersion;

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

    public int @Nullable [] preview() {
        return preview;
    }

    public int previewVersion() {
        return previewVersion;
    }

    /** Server: re-captures the room and, if it changed, sends the new miniature to nearby players. */
    void serverTick(ServerLevel level) {
        int interval = MicrocosmConfig.PREVIEW_INTERVAL.getAsInt();
        if (interval <= 0 || (level.getGameTime() + worldPosition.asLong()) % interval != 0) {
            return;
        }
        RoomManager.Room room = RoomManager.roomOf(level, worldPosition, false);
        if (room == null) {
            return;
        }
        int[] states = ViewerSessions.capture(room);
        int hash = Arrays.hashCode(states);
        if (preview == null || hash != previewHash || !Arrays.equals(states, preview)) {
            preview = states;
            previewHash = hash;
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    /** Only the miniature goes to clients: a palette plus one byte per room block (or an int if needed). */
    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        if (preview != null) {
            int[] palette = Arrays.stream(preview).distinct().toArray();
            Map<Integer, Integer> index = new HashMap<>();
            for (int i = 0; i < palette.length; i++) {
                index.put(palette[i], i);
            }
            tag.putIntArray("preview_palette", palette);
            if (palette.length <= 256) {
                // Four palette indices per int.
                int[] cells = new int[preview.length / 4];
                for (int i = 0; i < preview.length; i++) {
                    cells[i >> 2] |= index.get(preview[i]) << ((i & 3) * 8);
                }
                tag.putIntArray("preview_cells", cells);
            } else {
                tag.putIntArray("preview", preview);
            }
        }
        return tag;
    }

    private void readPreview(ValueInput input) {
        int[] palette = input.getIntArray("preview_palette").orElse(null);
        if (palette == null) {
            return;
        }
        int[] states = input.getIntArray("preview").orElse(null);
        if (states == null) {
            int[] cells = input.getIntArray("preview_cells").orElse(null);
            if (cells == null) {
                return;
            }
            states = new int[cells.length * 4];
            for (int i = 0; i < states.length; i++) {
                int slot = cells[i >> 2] >>> ((i & 3) * 8) & 0xFF;
                states[i] = slot < palette.length ? palette[slot] : 0;
            }
        }
        if (states.length == 16 * 16 * 16) {
            preview = states;
            previewVersion++;
        }
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        readPreview(input);
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
