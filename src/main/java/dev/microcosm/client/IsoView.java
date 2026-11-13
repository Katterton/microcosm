package dev.microcosm.client;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntUnaryOperator;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/**
 * Isometric 3D view of a 16x16x16 room, drawn back to front. Everything above the cut height is hidden so the inside
 * can be seen. Colour mode uses the blocks' item icons (which are isometric cubes); otherwise blocks are drawn as
 * shaded cubes in the given tint.
 */
final class IsoView {
    /** Half width and half height of a cube's top face, and the height of its sides, in GUI pixels. */
    static final float HW = 5f;
    static final float HH = 2.5f;
    static final float VH = 6.1f;
    /** Item icons show a cube about 14.1 px wide; scale them to our cube width. */
    private static final float ICON_SCALE = HW * 2 / 14.14f;

    /** A drawn block: room-local position and the screen centre of its top face. */
    record Drawn(BlockPos pos, float cx, float cy) {}

    /** A block under the mouse and the face that was hit, in room coordinates. */
    record Hit(BlockPos pos, Direction face) {}

    interface States {
        BlockState get(int x, int y, int z);
    }

    interface Painter {
        /** Draws {@code state} as a cube; returns false to fall back to a shaded cube. */
        boolean icon(GuiGraphicsExtractor graphics, BlockState state, float x, float y, float scale);

        int tint(BlockState state);
    }

    private final List<Drawn> drawn = new ArrayList<>();
    private int rotation;

    void rotate() {
        rotation = (rotation + 1) & 3;
    }

    /** Room x/z of view position (vx, vz). */
    private int[] toRoom(int vx, int vz) {
        return switch (rotation) {
            case 1 -> new int[] {vz, 15 - vx};
            case 2 -> new int[] {15 - vx, 15 - vz};
            case 3 -> new int[] {15 - vz, vx};
            default -> new int[] {vx, vz};
        };
    }

    /** The room direction the view's +x or +z axis points to. */
    private Direction viewAxis(boolean xAxis) {
        Direction[] plusX = {Direction.EAST, Direction.NORTH, Direction.WEST, Direction.SOUTH};
        Direction[] plusZ = {Direction.SOUTH, Direction.EAST, Direction.NORTH, Direction.WEST};
        return (xAxis ? plusX : plusZ)[rotation];
    }

    void draw(GuiGraphicsExtractor graphics, States states, int cut, int left, int top, int size, boolean icons, Painter painter) {
        drawn.clear();
        float ox = left + size / 2f;
        float oy = top + 15 * VH + HH + 3;
        for (int sum = 0; sum <= 30; sum++) {
            for (int y = 0; y <= cut; y++) {
                for (int vx = Math.max(0, sum - 15); vx <= Math.min(15, sum); vx++) {
                    int vz = sum - vx;
                    int[] room = toRoom(vx, vz);
                    BlockState state = states.get(room[0], y, room[1]);
                    if (state.isAir() || hidden(states, vx, y, vz, cut)) {
                        continue;
                    }
                    float cx = ox + (vx - vz) * HW;
                    float cy = oy + (vx + vz) * HH - y * VH;
                    if (!icons || !painter.icon(graphics, state, cx - 8 * ICON_SCALE, cy + VH / 2 - 8 * ICON_SCALE, ICON_SCALE)) {
                        cube(graphics, cx, cy, painter.tint(state));
                    }
                    drawn.add(new Drawn(new BlockPos(room[0], y, room[1]), cx, cy));
                }
            }
        }
    }

    /** A block is hidden when all three faces we could see are covered by opaque blocks. */
    private boolean hidden(States states, int vx, int y, int vz, int cut) {
        return opaque(states, vx + 1, y, vz, cut) && opaque(states, vx, y, vz + 1, cut) && opaque(states, vx, y + 1, vz, cut);
    }

    private boolean opaque(States states, int vx, int y, int vz, int cut) {
        if (vx > 15 || vz > 15 || y > cut) {
            return false;
        }
        int[] room = toRoom(vx, vz);
        return states.get(room[0], y, room[1]).canOcclude();
    }

    private static void cube(GuiGraphicsExtractor graphics, float cx, float cy, int rgb) {
        int left = 0xFF000000 | shade(rgb, 78);
        int right = 0xFF000000 | shade(rgb, 60);
        for (int px = (int) Math.floor(cx - HW); px < Math.ceil(cx + HW); px++) {
            float dx = px + 0.5f - cx;
            float edge = cy + HH * (1 - Math.abs(dx) / HW);
            graphics.fill(px, Math.round(edge), px + 1, Math.round(edge + VH), dx < 0 ? left : right);
        }
        int topColor = 0xFF000000 | rgb;
        for (int py = (int) Math.floor(cy - HH); py < Math.ceil(cy + HH); py++) {
            float half = HW * (1 - Math.abs(py + 0.5f - cy) / HH);
            if (half > 0) {
                graphics.fill(Math.round(cx - half), py, Math.round(cx + half), py + 1, topColor);
            }
        }
    }

    private static int shade(int rgb, int percent) {
        IntUnaryOperator f = c -> c * percent / 100;
        return f.applyAsInt(rgb >> 16 & 255) << 16 | f.applyAsInt(rgb >> 8 & 255) << 8 | f.applyAsInt(rgb & 255);
    }

    /** The front-most block under the mouse and which of its visible faces was hit. */
    @Nullable Hit pick(double mouseX, double mouseY) {
        for (int i = drawn.size() - 1; i >= 0; i--) {
            Drawn d = drawn.get(i);
            float dx = (float) mouseX - d.cx();
            if (Math.abs(dx) > HW) {
                continue;
            }
            float slope = HH * (1 - Math.abs(dx) / HW);
            float my = (float) mouseY;
            if (my < d.cy() - slope || my > d.cy() + slope + VH) {
                continue;
            }
            Direction face = my <= d.cy() + slope ? Direction.UP : viewAxis(dx >= 0);
            return new Hit(d.pos(), face);
        }
        return null;
    }

    /** Icon painter for colour mode: block items render as isometric cubes. */
    static boolean blockIcon(GuiGraphicsExtractor graphics, BlockState state, float x, float y, float scale) {
        Block block = state.getBlock();
        if (!(block.asItem() instanceof BlockItem item) || item.getBlock() != block) {
            return false;
        }
        graphics.pose().pushMatrix();
        graphics.pose().translate(x, y);
        graphics.pose().scale(scale, scale);
        graphics.fakeItem(new ItemStack(item), 0, 0);
        graphics.pose().popMatrix();
        return true;
    }
}
