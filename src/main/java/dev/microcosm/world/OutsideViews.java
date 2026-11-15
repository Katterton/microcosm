package dev.microcosm.world;

import dev.microcosm.MicrocosmConfig;
import dev.microcosm.network.OutsideViewPayload;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.network.PacketDistributor;

/** Sends players inside a room what surrounds their cell one level up, whenever that changes. */
public final class OutsideViews {
    private static final int INTERVAL = 20;

    private record Sent(BlockPos roomOrigin, int hash) {}

    private static final Map<UUID, Sent> SENT = new HashMap<>();

    private OutsideViews() {}

    public static void onServerTick(MinecraftServer server) {
        if (server.getTickCount() % INTERVAL != 0) {
            return;
        }
        int radius = MicrocosmConfig.OUTSIDE_VIEW_RADIUS.getAsInt();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            update(player, radius);
        }
        SENT.keySet().removeIf(id -> server.getPlayerList().getPlayer(id) == null);
    }

    private static void update(ServerPlayer player, int radius) {
        ServerLevel level = player.level();
        RoomManager.Parent parent = radius > 0 ? RoomManager.parentOf(level, player.blockPosition()) : null;
        if (parent == null) {
            if (SENT.remove(player.getUUID()) != null) {
                PacketDistributor.sendToPlayer(player, OutsideViewPayload.clear());
            }
            return;
        }
        int size = radius * 2 + 1;
        int[] states = new int[size * size * size];
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int dy = -radius; dy <= radius; dy++) {
            for (int dz = -radius; dz <= radius; dz++) {
                for (int dx = -radius; dx <= radius; dx++) {
                    pos.setWithOffset(parent.pos(), dx, dy, dz);
                    boolean self = dx == 0 && dy == 0 && dz == 0;
                    states[((dy + radius) * size + (dz + radius)) * size + (dx + radius)] =
                            self || !parent.level().isLoaded(pos) ? 0 : Block.getId(parent.level().getBlockState(pos));
                }
            }
        }
        BlockPos origin = RoomGeometry.slotOrigin(player.blockPosition());
        Sent sent = new Sent(origin, Objects.hash(Arrays.hashCode(states), parent.pos(), radius));
        if (!sent.equals(SENT.get(player.getUUID()))) {
            SENT.put(player.getUUID(), sent);
            PacketDistributor.sendToPlayer(player, new OutsideViewPayload(origin, parent.pos(), radius, states));
        }
    }
}
