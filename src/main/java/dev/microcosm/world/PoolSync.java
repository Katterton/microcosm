package dev.microcosm.world;

import dev.microcosm.network.PoolSyncPayload;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.PacketDistributor;

/** Tells clients which pool dimension is at which scale level. */
public final class PoolSync {
    private PoolSync() {}

    static PoolSyncPayload payload() {
        Map<Identifier, Integer> depths = new HashMap<>();
        MicrocosmLevels.all().forEach((key, depth) -> depths.put(key.identifier(), depth));
        return new PoolSyncPayload(depths);
    }

    public static void send(ServerPlayer player) {
        PacketDistributor.sendToPlayer(player, payload());
    }

    static void broadcast(MinecraftServer server) {
        PacketDistributor.sendToAllPlayers(payload());
    }

    /** Client side: adds the received depths (never clears, the integrated server shares this table). */
    public static void receive(PoolSyncPayload payload) {
        Map<ResourceKey<Level>, Integer> depths = new HashMap<>(MicrocosmLevels.all());
        payload.depths().forEach((id, depth) -> depths.put(ResourceKey.create(Registries.DIMENSION, id), depth));
        MicrocosmLevels.setAll(depths);
    }
}
