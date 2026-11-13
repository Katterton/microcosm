package dev.microcosm.world;

import dev.microcosm.Microcosm;
import dev.microcosm.MicrocosmConfig;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Moves players into and out of rooms. Entering shrinks the player over a short animation and then moves them into
 * the room; leaving moves them out tiny and lets them grow back to full size.
 */
public final class ShiftManager {
    private static final Identifier SCALE_ID = Microcosm.id("shift_scale");
    private static final int ANIMATION_TICKS = 12;
    private static final double MIN_SCALE = 0.1;

    private record Shift(boolean entering, ServerLevel target, Vec3 position, int depth) {}

    private static final Map<UUID, Shift> ACTIVE = new HashMap<>();
    private static final Map<UUID, Integer> PROGRESS = new HashMap<>();

    private ShiftManager() {}

    public static boolean isShifting(Player player) {
        return ACTIVE.containsKey(player.getUUID());
    }

    /**
     * Starts entering the room of the cell at {@code cellPos}.
     *
     * @return null on success, otherwise a translation key explaining why not
     */
    public static @Nullable String enter(ServerPlayer player, ServerLevel level, BlockPos cellPos) {
        if (!MicrocosmConfig.ENTERING_ENABLED.getAsBoolean()) {
            return "message.microcosm.shift.disabled";
        }
        if (isShifting(player)) {
            return "message.microcosm.shift.busy";
        }
        int depth = MicrocosmLevels.depth(level) + 1;
        if (depth > MicrocosmConfig.ENTERING_MAX_DEPTH.getAsInt()) {
            return "message.microcosm.shift.too_deep";
        }
        RoomManager.Room room = RoomManager.roomOf(level, cellPos, true);
        if (room == null) {
            return "message.microcosm.shift.too_deep";
        }
        BlockPos spot = findSpotInRoom(room);
        if (spot == null) {
            return "message.microcosm.shift.room_full";
        }
        ACTIVE.put(player.getUUID(), new Shift(true, room.level(), Vec3.atBottomCenterOf(spot), depth));
        PROGRESS.put(player.getUUID(), 0);
        level.playSound(null, player.blockPosition(), SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 0.8f, 1.6f);
        return null;
    }

    /** Starts leaving the room the player is in; returns null on success or a translation key. */
    public static @Nullable String exit(ServerPlayer player, ServerLevel level) {
        if (isShifting(player)) {
            return "message.microcosm.shift.busy";
        }
        RoomManager.Parent parent = RoomManager.parentOf(level, player.blockPosition());
        if (parent == null) {
            return "message.microcosm.shift.not_in_room";
        }
        BlockPos spot = findExit(parent.level(), parent.pos());
        setScale(player, MIN_SCALE);
        teleport(player, parent.level(), Vec3.atBottomCenterOf(spot));
        ACTIVE.put(player.getUUID(), new Shift(false, parent.level(), Vec3.atBottomCenterOf(spot),
                MicrocosmLevels.depth(parent.level())));
        PROGRESS.put(player.getUUID(), 0);
        parent.level().playSound(null, spot, SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 0.8f, 1.6f);
        return null;
    }

    public static void onServerTick(MinecraftServer server) {
        Iterator<Map.Entry<UUID, Shift>> it = ACTIVE.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Shift> entry = it.next();
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            if (player == null || !player.isAlive()) {
                PROGRESS.remove(entry.getKey());
                it.remove();
                continue;
            }
            int tick = PROGRESS.merge(entry.getKey(), 1, Integer::sum);
            float t = Math.min(1f, tick / (float) ANIMATION_TICKS);
            Shift shift = entry.getValue();
            if (shift.entering) {
                setScale(player, 1 - (1 - MIN_SCALE) * t);
                if (t >= 1) {
                    teleport(player, shift.target, shift.position);
                    setScale(player, 1);
                    player.sendOverlayMessage(Component.translatable("message.microcosm.shift.entered", shift.depth));
                    PROGRESS.remove(entry.getKey());
                    it.remove();
                }
            } else {
                setScale(player, MIN_SCALE + (1 - MIN_SCALE) * t);
                if (t >= 1) {
                    setScale(player, 1);
                    player.sendOverlayMessage(Component.translatable("message.microcosm.shift.left", shift.depth));
                    PROGRESS.remove(entry.getKey());
                    it.remove();
                }
            }
        }
    }

    private static void teleport(ServerPlayer player, ServerLevel level, Vec3 pos) {
        player.teleport(new TeleportTransition(level, pos, Vec3.ZERO, player.getYRot(), player.getXRot(),
                Set.of(), TeleportTransition.DO_NOTHING));
        player.resetFallDistance();
    }

    /** Scales the player with a temporary modifier; 1 removes it. Not saved, so a crash or logout resets it. */
    private static void setScale(ServerPlayer player, double scale) {
        AttributeInstance attribute = player.getAttribute(Attributes.SCALE);
        if (attribute == null) {
            return;
        }
        attribute.removeModifier(SCALE_ID);
        if (scale < 0.999) {
            attribute.addTransientModifier(new AttributeModifier(SCALE_ID, scale - 1,
                    AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        }
    }

    /** A free spot inside the room with room to stand, preferring the floor near the middle. */
    public static @Nullable BlockPos findSpotInRoom(RoomManager.Room room) {
        ServerLevel level = room.level();
        BlockPos best = null;
        double bestScore = Double.MAX_VALUE;
        for (int y = 0; y < 15; y++) {
            for (int z = 0; z < 16; z++) {
                for (int x = 0; x < 16; x++) {
                    BlockPos pos = room.origin().offset(x, y, z);
                    if (!fits(level, pos)) {
                        continue;
                    }
                    BlockPos below = pos.below();
                    boolean standing = !level.getBlockState(below).getCollisionShape(level, below).isEmpty();
                    double score = (x - 7.5) * (x - 7.5) + (z - 7.5) * (z - 7.5) + y * 4 + (standing ? 0 : 10000);
                    if (score < bestScore) {
                        bestScore = score;
                        best = pos;
                    }
                }
            }
        }
        return best;
    }

    /** Where to put a player leaving the room of the cell at {@code cellPos}. */
    static BlockPos findExit(ServerLevel level, BlockPos cellPos) {
        BlockPos above = cellPos.above();
        if (fits(level, above)) {
            return above;
        }
        for (Direction dir : Direction.Plane.HORIZONTAL) {
            BlockPos side = cellPos.relative(dir);
            if (fits(level, side)) {
                return side;
            }
        }
        for (int dy = 2; dy < 16; dy++) {
            BlockPos up = cellPos.above(dy);
            if (fits(level, up)) {
                return up;
            }
        }
        return above;
    }

    private static boolean fits(ServerLevel level, BlockPos feet) {
        return free(level, feet) && free(level, feet.above());
    }

    private static boolean free(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return state.getCollisionShape(level, pos).isEmpty() && state.getFluidState().isEmpty();
    }

    /** Whether any player is inside the room of the cell at {@code cellPos}. */
    public static boolean hasPlayersInside(ServerLevel parent, BlockPos cellPos) {
        RoomManager.Room room = RoomManager.roomOf(parent, cellPos, false);
        return room != null && !room.level().getEntitiesOfClass(Player.class,
                new AABB(Vec3.atLowerCornerOf(room.origin()), Vec3.atLowerCornerOf(room.max()).add(1, 1, 1))).isEmpty();
    }
}
