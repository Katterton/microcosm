package dev.microcosm.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.microcosm.block.CellBlock;
import dev.microcosm.network.OutsideViewPayload;
import dev.microcosm.world.MicrocosmLevels;
import dev.microcosm.world.RoomGeometry;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.SubmitCustomGeometryEvent;
import org.jspecify.annotations.Nullable;

/**
 * Inside a room, draws the world around its cell one level up, 16 times enlarged: each of those blocks becomes a
 * 16x16x16 block around the room, so one texture pixel is one room block.
 */
public final class OutsideViewRenderer {
    @SuppressWarnings("deprecation") // vanilla block entity renderers use the same constant
    private static final RenderType RENDER_TYPE = RenderTypes.entityCutout(TextureAtlas.LOCATION_BLOCKS);
    private static final int SCALE = RoomGeometry.ROOM;
    /** Pulls the enlarged blocks back slightly so their faces never share a plane with the room walls. */
    private static final float GAP = 0.02f;

    private static @Nullable OutsideViewPayload view;
    private static @Nullable List<Face> faces;

    private record Face(float x, float y, float z, Direction dir, TextureAtlasSprite sprite, int color) {}

    private OutsideViewRenderer() {}

    public static void receive(OutsideViewPayload payload) {
        view = payload.radius() > 0 ? payload : null;
        faces = null;
    }

    public static void onSubmit(SubmitCustomGeometryEvent event) {
        OutsideViewPayload current = view;
        Minecraft minecraft = Minecraft.getInstance();
        if (current == null || minecraft.level == null || minecraft.player == null
                || MicrocosmLevels.depth(minecraft.level) == 0) {
            return;
        }
        RoomGeometry.Slot slot = RoomGeometry.slotAt(minecraft.player.blockPosition());
        if (slot == null || !slot.origin().equals(current.roomOrigin())) {
            return;
        }
        if (faces == null) {
            faces = build(current);
        }
        List<Face> list = faces;
        if (list.isEmpty()) {
            return;
        }
        Vec3 camera = event.getLevelRenderState().cameraRenderState.pos;
        PoseStack poseStack = event.getPoseStack();
        poseStack.pushPose();
        BlockPos origin = current.roomOrigin();
        poseStack.translate(origin.getX() - camera.x, origin.getY() - camera.y, origin.getZ() - camera.z);
        // Full sky light, so the lightmap darkens the enlarged world at night like the real one.
        int light = LightCoordsUtil.pack(0, 15);
        event.getSubmitNodeCollector().submitCustomGeometry(poseStack, RENDER_TYPE, (pose, buffer) -> {
            for (Face face : list) {
                TextureAtlasSprite sprite = face.sprite;
                MiniRoomMesh.face(pose, buffer, face.x + GAP, face.y + GAP, face.z + GAP, SCALE - 2 * GAP, face.dir,
                        sprite.getU0(), sprite.getV0(), sprite.getU1(), sprite.getV1(), face.color, light);
            }
        });
        poseStack.popPose();
    }

    /** Visible faces of the enlarged blocks, positioned relative to the room origin. */
    private static List<Face> build(OutsideViewPayload view) {
        int r = view.radius();
        int size = view.size();
        BlockState[] states = new BlockState[view.states().length];
        for (int i = 0; i < states.length; i++) {
            states[i] = Block.stateById(view.states()[i]);
        }
        List<Face> out = new ArrayList<>();
        for (int dy = -r; dy <= r; dy++) {
            for (int dz = -r; dz <= r; dz++) {
                for (int dx = -r; dx <= r; dx++) {
                    BlockState state = states[((dy + r) * size + (dz + r)) * size + (dx + r)];
                    if (state.isAir() || hasRealRoom(view.cellPos(), dx, dy, dz, state)) {
                        continue;
                    }
                    MiniRoomMesh.FaceTexture[] textures = MiniRoomMesh.texturesOf(state);
                    for (Direction dir : Direction.values()) {
                        int nx = dx + dir.getStepX();
                        int ny = dy + dir.getStepY();
                        int nz = dz + dir.getStepZ();
                        if (Math.abs(nx) <= r && Math.abs(ny) <= r && Math.abs(nz) <= r) {
                            BlockState neighbor = states[((ny + r) * size + (nz + r)) * size + (nx + r)];
                            if (neighbor.canOcclude() && !hasRealRoom(view.cellPos(), nx, ny, nz, neighbor)) {
                                continue;
                            }
                        }
                        MiniRoomMesh.FaceTexture texture = textures[dir.ordinal()];
                        out.add(new Face(dx * SCALE, dy * SCALE, dz * SCALE, dir, texture.sprite(), texture.color()));
                    }
                }
            }
        }
        return out;
    }

    /**
     * Cells in the same section as ours have their real rooms exactly where their enlarged block would be (the
     * room layout mirrors the cells), so those are not drawn. That includes our own cell at offset 0.
     */
    private static boolean hasRealRoom(BlockPos cellPos, int dx, int dy, int dz, BlockState state) {
        return state.getBlock() instanceof CellBlock && RoomGeometry.sameSection(cellPos, cellPos.offset(dx, dy, dz));
    }
}
