package dev.microcosm.device;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jspecify.annotations.Nullable;

/**
 * A room access device. Placed next to a cell. Right-click a viewer to look into the room; right-click an actuator
 * (or sneak-right-click the field manipulator) to open its inventory.
 */
public class DeviceBlock extends Block implements EntityBlock {
    private final DeviceKind kind;
    private final MapCodec<DeviceBlock> codec;

    public DeviceBlock(Properties properties, DeviceKind kind) {
        super(properties);
        this.kind = kind;
        this.codec = simpleCodec(p -> new DeviceBlock(p, kind));
    }

    public DeviceKind kind() {
        return kind;
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return codec;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new DeviceBlockEntity(pos, state);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide() || !kind.isActuator()) {
            return null;
        }
        return (l, pos, s, be) -> {
            if (be instanceof DeviceBlockEntity device && l instanceof ServerLevel serverLevel) {
                device.serverTick(serverLevel);
            }
        };
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!(level instanceof ServerLevel serverLevel) || !(player instanceof ServerPlayer serverPlayer)
                || !(level.getBlockEntity(pos) instanceof DeviceBlockEntity device)) {
            return InteractionResult.SUCCESS;
        }
        boolean openInventory = kind.hasInventory && (!kind.isViewer() || player.isShiftKeyDown());
        if (openInventory) {
            player.openMenu(device);
        } else if (RoomPath.adjacentCell(serverLevel, pos) == null) {
            player.sendOverlayMessage(Component.translatable("message.microcosm.no_cell"));
        } else {
            ViewerSessions.open(serverPlayer, serverLevel, pos);
        }
        return InteractionResult.SUCCESS;
    }
}
