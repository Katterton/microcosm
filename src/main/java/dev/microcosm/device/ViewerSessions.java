package dev.microcosm.device;

import dev.microcosm.MicrocosmConfig;
import dev.microcosm.block.CellBlock;
import dev.microcosm.network.ViewPayload;
import dev.microcosm.network.ViewerRequestPayload;
import dev.microcosm.world.MicrocosmLevels;
import dev.microcosm.world.RoomManager;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jspecify.annotations.Nullable;

/** Server side of open viewer screens: which room each player looks at, refreshes, scans and actions. */
public final class ViewerSessions {
    private static final double MAX_DISTANCE_SQ = 8 * 8;
    /** Blind viewers still get inventory and queue updates at this interval. */
    private static final int STATUS_INTERVAL = 20;
    private static final int[] NO_DATA = new int[0];

    private static final Map<UUID, Session> SESSIONS = new HashMap<>();

    private static final class Session {
        final ResourceKey<Level> dimension;
        final BlockPos viewerPos;
        final List<BlockPos> path = new ArrayList<>();
        long nextRefresh;
        int[] data = NO_DATA;
        long dataTime;
        long scanDone = -1;
        String status = "";

        Session(ResourceKey<Level> dimension, BlockPos viewerPos, BlockPos rootCell) {
            this.dimension = dimension;
            this.viewerPos = viewerPos;
            this.path.add(rootCell);
        }
    }

    private ViewerSessions() {}

    public static void open(ServerPlayer player, ServerLevel level, BlockPos viewerPos) {
        BlockPos cell = RoomPath.adjacentCell(level, viewerPos);
        if (cell == null) {
            return;
        }
        Session session = new Session(level.dimension(), viewerPos, cell);
        SESSIONS.put(player.getUUID(), session);
        refresh(player, level, session, true);
    }

    public static void close(ServerPlayer player) {
        SESSIONS.remove(player.getUUID());
    }

    public static void onServerTick(MinecraftServer server) {
        Iterator<Map.Entry<UUID, Session>> it = SESSIONS.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Session> entry = it.next();
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            Session session = entry.getValue();
            ServerLevel level = player == null ? null : server.getLevel(session.dimension);
            if (player == null || level == null || viewer(level, session) == null
                    || player.level() != level || player.distanceToSqr(session.viewerPos.getCenter()) > MAX_DISTANCE_SQ) {
                it.remove();
                continue;
            }
            if (level.getGameTime() >= session.nextRefresh
                    || (session.scanDone >= 0 && level.getGameTime() >= session.scanDone)) {
                refresh(player, level, session, false);
            }
        }
    }

    public static void handle(ServerPlayer player, ViewerRequestPayload request) {
        Session session = SESSIONS.get(player.getUUID());
        if (session == null) {
            return;
        }
        ServerLevel level = player.level().getServer().getLevel(session.dimension);
        DeviceBlockEntity viewer = level == null ? null : viewer(level, session);
        if (viewer == null) {
            SESSIONS.remove(player.getUUID());
            return;
        }
        switch (request.op()) {
            case CLOSE -> {
                SESSIONS.remove(player.getUUID());
                return;
            }
            case ZOOM_OUT -> {
                if (session.path.size() > 1) {
                    session.path.removeLast();
                    resetView(session);
                }
            }
            case ZOOM_IN -> zoomIn(level, viewer, session, request.local());
            case SCAN -> startScan(level, viewer, session);
            case ACTION -> session.status = act(level, session, request).name().toLowerCase();
        }
        refresh(player, level, session, true);
    }

    private static void zoomIn(ServerLevel level, DeviceBlockEntity viewer, Session session, BlockPos local) {
        RoomManager.Room room = RoomPath.resolve(level, session.path);
        if (room == null || !RoomPath.isLocal(local)) {
            return;
        }
        BlockPos cell = room.origin().offset(local);
        if (!(room.level().getBlockState(cell).getBlock() instanceof CellBlock)) {
            return;
        }
        if (MicrocosmLevels.depth(room.level()) + 1 > viewer.kind().viewerTier) {
            session.status = "too_deep";
            return;
        }
        session.path.add(cell);
        resetView(session);
    }

    private static void resetView(Session session) {
        session.data = NO_DATA;
        session.scanDone = -1;
        session.nextRefresh = 0;
        session.status = "";
    }

    private static void startScan(ServerLevel level, DeviceBlockEntity viewer, Session session) {
        if (!viewer.kind().isBlind() || session.scanDone >= 0) {
            return;
        }
        MicrocosmConfig.Tier tier = viewer.tierConfig();
        if (!viewer.consumeEnergy(tier.viewEnergy.getAsInt())) {
            session.status = "no_energy";
            return;
        }
        session.scanDone = level.getGameTime() + tier.viewTicks.getAsInt();
        session.status = "scanning";
    }

    private static DeviceAction.Result act(ServerLevel level, Session session, ViewerRequestPayload request) {
        DeviceBlockEntity actuator = actuator(level, session.path.getFirst(), depthOf(level, session));
        if (actuator == null) {
            return DeviceAction.Result.NO_ACTUATOR;
        }
        DeviceAction.Type[] types = DeviceAction.Type.values();
        if (request.action() < 0 || request.action() >= types.length || !RoomPath.isLocal(request.local())) {
            return DeviceAction.Result.NO_EFFECT;
        }
        return actuator.enqueue(new DeviceAction(List.copyOf(session.path), request.local().immutable(),
                types[request.action()], request.slot()));
    }

    /**
     * The actuator next to the root cell for a room at {@code depth}: the lowest tier that still reaches it (the
     * fastest), otherwise the highest tier there is.
     */
    public static @Nullable DeviceBlockEntity actuator(ServerLevel level, BlockPos rootCell, int depth) {
        DeviceBlockEntity best = null;
        for (Direction dir : Direction.values()) {
            if (level.getBlockEntity(rootCell.relative(dir)) instanceof DeviceBlockEntity device && device.kind().isActuator()) {
                if (best == null || better(device.kind().actuatorTier, best.kind().actuatorTier, depth)) {
                    best = device;
                }
            }
        }
        return best;
    }

    private static boolean better(int tier, int current, int depth) {
        boolean reaches = tier >= depth;
        boolean currentReaches = current >= depth;
        if (reaches != currentReaches) {
            return reaches;
        }
        return reaches ? tier < current : tier > current;
    }

    private static int depthOf(ServerLevel level, Session session) {
        RoomManager.Room room = RoomPath.resolve(level, session.path);
        return room == null ? session.path.size() : MicrocosmLevels.depth(room.level());
    }

    private static @Nullable DeviceBlockEntity viewer(ServerLevel level, Session session) {
        return level.isLoaded(session.viewerPos) && level.getBlockEntity(session.viewerPos) instanceof DeviceBlockEntity device
                && device.kind().isViewer() ? device : null;
    }

    private static void refresh(ServerPlayer player, ServerLevel level, Session session, boolean force) {
        DeviceBlockEntity viewer = viewer(level, session);
        if (viewer == null) {
            return;
        }
        long now = level.getGameTime();
        MicrocosmConfig.Tier tier = viewer.tierConfig();
        RoomManager.Room room = RoomPath.resolve(level, session.path);
        if (room == null) {
            // The cell we zoomed into is gone; fall back to the outermost room.
            while (session.path.size() > 1 && room == null) {
                session.path.removeLast();
                room = RoomPath.resolve(level, session.path);
            }
            session.data = NO_DATA;
            session.status = "room_gone";
        }
        boolean due = now >= session.nextRefresh;
        if (viewer.kind().isBlind()) {
            if (session.scanDone >= 0 && now >= session.scanDone && room != null) {
                session.data = capture(room);
                session.dataTime = now;
                session.scanDone = -1;
                session.status = "";
            }
            session.nextRefresh = now + STATUS_INTERVAL;
        } else if (due && room != null) {
            if (viewer.consumeEnergy(tier.viewEnergy.getAsInt())) {
                session.data = capture(room);
                session.dataTime = now;
                if ("no_energy".equals(session.status)) {
                    session.status = "";
                }
            } else {
                session.data = NO_DATA;
                session.status = "no_energy";
            }
            session.nextRefresh = now + tier.viewTicks.getAsInt();
        } else if (!force) {
            return;
        }
        DeviceBlockEntity actuator = actuator(level, session.path.getFirst(),
                room == null ? session.path.size() : MicrocosmLevels.depth(room.level()));
        if (actuator != null && "queued".equals(session.status) && actuator.queueSize() == 0) {
            session.status = "";
        }
        List<ItemStack> items = new ArrayList<>();
        if (actuator != null) {
            for (int i = 0; i < actuator.getContainerSize(); i++) {
                items.add(actuator.getItem(i).copy());
            }
        }
        PacketDistributor.sendToPlayer(player, new ViewPayload(
                session.viewerPos,
                viewer.kind().viewerTier,
                room == null ? 0 : MicrocosmLevels.depth(room.level()),
                session.path.size(),
                session.data,
                (int) Math.min(Integer.MAX_VALUE, now - session.dataTime),
                session.scanDone >= 0,
                actuator == null ? 0 : actuator.kind().actuatorTier,
                items,
                actuator == null ? 0 : actuator.queueSize(),
                actuator == null ? "" : actuator.lastResult().name().toLowerCase(),
                viewer.energyStored(),
                viewer.energyCapacity(),
                actuator == null ? 0 : actuator.energyStored(),
                actuator == null ? 0 : actuator.energyCapacity(),
                session.status));
    }

    /** The room's block states in viewer order. */
    public static int[] capture(RoomManager.Room room) {
        int[] states = new int[16 * 16 * 16];
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int y = 0; y < 16; y++) {
            for (int z = 0; z < 16; z++) {
                for (int x = 0; x < 16; x++) {
                    states[(y * 16 + z) * 16 + x] = Block.getId(room.level().getBlockState(pos.setWithOffset(room.origin(), x, y, z)));
                }
            }
        }
        return states;
    }
}
