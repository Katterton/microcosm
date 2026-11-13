package dev.microcosm.block;

import dev.microcosm.ModRegistry;
import dev.microcosm.world.RoomManager;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Keeps the cell's room loaded while the cell itself is loaded. */
public class CellBlockEntity extends BlockEntity {
    private boolean ticketHeld;

    public CellBlockEntity(BlockPos pos, BlockState state) {
        super(ModRegistry.CELL_BLOCK_ENTITY.get(), pos, state);
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
