package dev.microcosm.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.color.block.BlockTintSource;
import net.minecraft.client.renderer.block.BlockStateModelSet;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import dev.microcosm.block.CellBlockEntity;
import org.jspecify.annotations.Nullable;

/**
 * The visible faces of a room's miniature, built once per room change. Each room block becomes a 1x1x1 cube in room
 * coordinates (0..16) textured with the block's own face texture; faces hidden behind opaque neighbours are skipped.
 */
final class MiniRoomMesh {
    /** Per face: x, y, z, direction, u0, v0, u1, v1, colour. */
    private final float[] faces;
    private final int count;
    private final BlockStateModelSet models;
    private final int version;

    private static final Map<CellBlockEntity, MiniRoomMesh> CACHE = new WeakHashMap<>();
    private static final Map<BlockState, FaceTexture[]> TEXTURES = new IdentityHashMap<>();
    private static @Nullable BlockStateModelSet texturesFor;

    private record FaceTexture(TextureAtlasSprite sprite, int color) {}

    private MiniRoomMesh(float[] faces, int count, BlockStateModelSet models, int version) {
        this.faces = faces;
        this.count = count;
        this.models = models;
        this.version = version;
    }

    boolean isEmpty() {
        return count == 0;
    }

    /** The mesh for a cell, rebuilt when its preview or the loaded resource packs changed. */
    static @Nullable MiniRoomMesh of(CellBlockEntity cell) {
        int[] preview = cell.preview();
        if (preview == null) {
            return null;
        }
        BlockStateModelSet models = Minecraft.getInstance().getModelManager().getBlockStateModelSet();
        MiniRoomMesh mesh = CACHE.get(cell);
        if (mesh == null || mesh.version != cell.previewVersion() || mesh.models != models) {
            mesh = build(preview, models, cell.previewVersion());
            CACHE.put(cell, mesh);
        }
        return mesh;
    }

    private static MiniRoomMesh build(int[] preview, BlockStateModelSet models, int version) {
        if (texturesFor != models) {
            TEXTURES.clear();
            texturesFor = models;
        }
        BlockState[] states = new BlockState[preview.length];
        for (int i = 0; i < preview.length; i++) {
            states[i] = Block.stateById(preview[i]);
        }
        List<Float> out = new ArrayList<>();
        int count = 0;
        for (int y = 0; y < 16; y++) {
            for (int z = 0; z < 16; z++) {
                for (int x = 0; x < 16; x++) {
                    BlockState state = states[(y * 16 + z) * 16 + x];
                    if (state.isAir()) {
                        continue;
                    }
                    FaceTexture[] textures = TEXTURES.computeIfAbsent(state, s -> textures(s, models));
                    for (Direction dir : Direction.values()) {
                        int nx = x + dir.getStepX();
                        int ny = y + dir.getStepY();
                        int nz = z + dir.getStepZ();
                        if (nx >= 0 && ny >= 0 && nz >= 0 && nx < 16 && ny < 16 && nz < 16) {
                            BlockState neighbor = states[(ny * 16 + nz) * 16 + nx];
                            if (neighbor.canOcclude() || neighbor == state && !neighbor.isAir()) {
                                continue;
                            }
                        }
                        FaceTexture texture = textures[dir.ordinal()];
                        TextureAtlasSprite sprite = texture.sprite;
                        out.add((float) x);
                        out.add((float) y);
                        out.add((float) z);
                        out.add((float) dir.ordinal());
                        out.add(sprite.getU0());
                        out.add(sprite.getV0());
                        out.add(sprite.getU1());
                        out.add(sprite.getV1());
                        out.add(Float.intBitsToFloat(texture.color));
                        count++;
                    }
                }
            }
        }
        float[] faces = new float[out.size()];
        for (int i = 0; i < faces.length; i++) {
            faces[i] = out.get(i);
        }
        return new MiniRoomMesh(faces, count, models, version);
    }

    /** The texture and tint of each face of a block, taken from its block model. */
    @SuppressWarnings("deprecation") // collectParts without a level is enough for a miniature
    private static FaceTexture[] textures(BlockState state, BlockStateModelSet models) {
        BlockStateModel model = models.get(state);
        List<BlockStateModelPart> parts = new ArrayList<>();
        model.collectParts(RandomSource.create(42L), parts);
        TextureAtlasSprite particle = models.getParticleMaterial(state).sprite();
        FaceTexture[] out = new FaceTexture[6];
        for (Direction dir : Direction.values()) {
            BakedQuad quad = findQuad(parts, dir);
            TextureAtlasSprite sprite = quad != null ? quad.materialInfo().sprite() : particle;
            int tintIndex = quad != null ? quad.materialInfo().tintIndex() : (state.getFluidState().isEmpty() ? -1 : 0);
            if (!TextureAtlas.LOCATION_BLOCKS.equals(sprite.atlasLocation())) {
                sprite = particle;
            }
            int color = 0xFFFFFFFF;
            if (tintIndex >= 0) {
                BlockTintSource tint = Minecraft.getInstance().getBlockColors().getTintSource(state, tintIndex);
                if (tint != null) {
                    color = 0xFF000000 | tint.color(state);
                }
            }
            out[dir.ordinal()] = new FaceTexture(sprite, color);
        }
        return out;
    }

    private static @Nullable BakedQuad findQuad(List<BlockStateModelPart> parts, Direction dir) {
        for (BlockStateModelPart part : parts) {
            List<BakedQuad> quads = part.getQuads(dir);
            if (!quads.isEmpty()) {
                return quads.getFirst();
            }
        }
        for (BlockStateModelPart part : parts) {
            for (BakedQuad quad : part.getQuads(null)) {
                if (quad.direction() == dir) {
                    return quad;
                }
            }
        }
        return null;
    }

    /** Corner offsets of each face, counter-clockwise seen from outside. */
    private static final float[][][] CORNERS = {
            {{0, 0, 1}, {0, 0, 0}, {1, 0, 0}, {1, 0, 1}}, // down
            {{0, 1, 0}, {0, 1, 1}, {1, 1, 1}, {1, 1, 0}}, // up
            {{1, 1, 0}, {1, 0, 0}, {0, 0, 0}, {0, 1, 0}}, // north
            {{0, 1, 1}, {0, 0, 1}, {1, 0, 1}, {1, 1, 1}}, // south
            {{0, 1, 0}, {0, 0, 0}, {0, 0, 1}, {0, 1, 1}}, // west
            {{1, 1, 1}, {1, 0, 1}, {1, 0, 0}, {1, 1, 0}}, // east
    };

    void emit(PoseStack.Pose pose, VertexConsumer buffer, int light) {
        Direction[] dirs = Direction.values();
        for (int f = 0; f < count; f++) {
            int o = f * 9;
            float x = faces[o];
            float y = faces[o + 1];
            float z = faces[o + 2];
            Direction dir = dirs[(int) faces[o + 3]];
            float u0 = faces[o + 4];
            float v0 = faces[o + 5];
            float u1 = faces[o + 6];
            float v1 = faces[o + 7];
            int color = Float.floatToRawIntBits(faces[o + 8]);
            float[][] corners = CORNERS[dir.ordinal()];
            float[] us = {u0, u0, u1, u1};
            float[] vs = {v0, v1, v1, v0};
            for (int i = 0; i < 4; i++) {
                buffer.addVertex(pose, x + corners[i][0], y + corners[i][1], z + corners[i][2])
                        .setColor(color)
                        .setUv(us[i], vs[i])
                        .setOverlay(OverlayTexture.NO_OVERLAY)
                        .setLight(light)
                        .setNormal(pose, dir.getStepX(), dir.getStepY(), dir.getStepZ());
            }
        }
    }
}
