package dev.microcosm.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.microcosm.block.CellBlockEntity;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/** Draws the cell's room as a miniature inside the cell's glass frame: one room block per pixel. */
public class CellRenderer implements BlockEntityRenderer<CellBlockEntity, CellRenderer.State> {
    @SuppressWarnings("deprecation") // vanilla block entity renderers use the same constant
    private static final RenderType RENDER_TYPE = RenderTypes.entityCutout(TextureAtlas.LOCATION_BLOCKS);
    /** The miniature fills the cell minus a one-pixel frame. */
    private static final float INSET = 1 / 16f;
    private static final float SCALE = (1 - 2 * INSET) / 16f;

    public static class State extends BlockEntityRenderState {
        @Nullable MiniRoomMesh mesh;
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(CellBlockEntity cell, State state, float partialTicks, Vec3 cameraPosition,
                                   ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
        BlockEntityRenderState.extractBase(cell, state, breakProgress);
        state.mesh = MiniRoomMesh.of(cell);
    }

    @Override
    public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        MiniRoomMesh mesh = state.mesh;
        if (mesh == null || mesh.isEmpty()) {
            return;
        }
        poseStack.pushPose();
        poseStack.translate(INSET, INSET, INSET);
        poseStack.scale(SCALE, SCALE, SCALE);
        int light = state.lightCoords;
        collector.submitCustomGeometry(poseStack, RENDER_TYPE, (pose, buffer) -> mesh.emit(pose, buffer, light));
        poseStack.popPose();
    }
}
