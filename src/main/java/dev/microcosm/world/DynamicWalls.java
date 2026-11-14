package dev.microcosm.world;

import dev.microcosm.block.CellWallBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.piston.PistonBaseBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.ProjectileImpactEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * Walls between a room and the block next to it (in the same 16x16x16 section) are not final: as soon as a player
 * comes close, a projectile hits one, or fluid or a piston pushes against one, the neighbouring block is opened up
 * (divided, or turned into an empty room if it is air). Rooms of neighbouring blocks touch without a wall, so from
 * then on everything simply crosses over. Walls to other sections, and to blocks that cannot be divided, stay.
 */
public final class DynamicWalls {
    /** How close (in blocks) a player gets to a wall before the neighbour opens. */
    private static final int REACH = 3;

    private DynamicWalls() {}

    public static void register() {
        NeoForge.EVENT_BUS.addListener(PlayerTickEvent.Post.class, DynamicWalls::onPlayerTick);
        NeoForge.EVENT_BUS.addListener(ProjectileImpactEvent.class, DynamicWalls::onProjectileImpact);
    }

    /**
     * Opens the block on the other side of the wall at {@code wallPos}.
     *
     * @return whether the wall is gone now
     */
    public static boolean openBehind(ServerLevel level, BlockPos wallPos) {
        if (!(level.getBlockState(wallPos).getBlock() instanceof CellWallBlock)) {
            return false;
        }
        // A wall always lies in the slot of the block it closes off, so its slot's block is the neighbour.
        RoomManager.Parent neighbour = RoomManager.parentOf(level, wallPos);
        if (neighbour == null || MicroBlocks.isMicro(neighbour.level().getBlockState(neighbour.pos()))) {
            return false;
        }
        return MicroBlocks.openForNeighbour(neighbour.level(), neighbour.pos())
                && !(level.getBlockState(wallPos).getBlock() instanceof CellWallBlock);
    }

    private static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.isSpectator()
                || MicrocosmLevels.depth(player.level()) == 0) {
            return;
        }
        ServerLevel level = player.level();
        BlockPos feet = player.blockPosition();
        for (BlockPos origin : new BlockPos[] {feet, feet.above()}) {
            for (Direction dir : Direction.values()) {
                for (int i = 1; i <= REACH; i++) {
                    BlockPos pos = origin.relative(dir, i);
                    BlockState state = level.getBlockState(pos);
                    if (state.getBlock() instanceof CellWallBlock) {
                        openBehind(level, pos);
                        break;
                    }
                    if (!state.isAir()) {
                        break;
                    }
                }
            }
        }
    }

    /** Ender pearls, arrows and the like fly on into the neighbouring block instead of hitting the wall. */
    private static void onProjectileImpact(ProjectileImpactEvent event) {
        HitResult hit = event.getRayTraceResult();
        if (hit.getType() == HitResult.Type.BLOCK && hit instanceof BlockHitResult blockHit
                && event.getProjectile().level() instanceof ServerLevel level && MicrocosmLevels.depth(level) > 0
                && openBehind(level, blockHit.getBlockPos())) {
            event.setCanceled(true);
        }
    }

    /** Fluids and pistons pressing against a wall open it, so water can run out and pistons push through. */
    public static void onWallNeighbourChanged(ServerLevel level, BlockPos wallPos) {
        for (Direction dir : Direction.values()) {
            BlockState neighbour = level.getBlockState(wallPos.relative(dir));
            if (!neighbour.getFluidState().isEmpty() || neighbour.getBlock() instanceof PistonBaseBlock) {
                openBehind(level, wallPos);
                return;
            }
        }
    }
}
