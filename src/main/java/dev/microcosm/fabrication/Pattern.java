package dev.microcosm.fabrication;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderSet;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/** A compiled 3D grid of cell matchers. {@code null} cells match anything. */
public final class Pattern {
    /** What one cell of a pattern requires. */
    public sealed interface Cell permits Air, Blocks {
        boolean matches(BlockState state);
    }

    public record Air() implements Cell {
        @Override
        public boolean matches(BlockState state) {
            return state.isAir();
        }
    }

    public record Blocks(HolderSet<Block> blocks) implements Cell {
        @Override
        public boolean matches(BlockState state) {
            return blocks.contains(state.typeHolder());
        }
    }

    public static final Air AIR = new Air();

    public final int sizeX;
    public final int sizeY;
    public final int sizeZ;
    private final @Nullable Cell[] cells;

    public Pattern(int sizeX, int sizeY, int sizeZ) {
        this.sizeX = sizeX;
        this.sizeY = sizeY;
        this.sizeZ = sizeZ;
        this.cells = new Cell[sizeX * sizeY * sizeZ];
    }

    private int index(int x, int y, int z) {
        return (y * sizeZ + z) * sizeX + x;
    }

    public @Nullable Cell get(int x, int y, int z) {
        return cells[index(x, y, z)];
    }

    public void set(int x, int y, int z, @Nullable Cell cell) {
        cells[index(x, y, z)] = cell;
    }

    /** This pattern rotated 90 degrees clockwise around the Y axis (seen from above). */
    public Pattern rotated() {
        Pattern out = new Pattern(sizeZ, sizeY, sizeX);
        for (int y = 0; y < sizeY; y++) {
            for (int z = 0; z < sizeZ; z++) {
                for (int x = 0; x < sizeX; x++) {
                    out.set(sizeZ - 1 - z, y, x, get(x, y, z));
                }
            }
        }
        return out;
    }

    /** The pattern in all four horizontal orientations. */
    public Pattern[] rotations() {
        Pattern r1 = rotated();
        Pattern r2 = r1.rotated();
        return new Pattern[] {this, r1, r2, r2.rotated()};
    }

    /** Whether the world matches this pattern with its minimum corner at {@code origin}. */
    public boolean matches(BlockGetterAccess level, BlockPos origin) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int y = 0; y < sizeY; y++) {
            for (int z = 0; z < sizeZ; z++) {
                for (int x = 0; x < sizeX; x++) {
                    Cell cell = get(x, y, z);
                    if (cell != null && !cell.matches(level.getBlockState(pos.setWithOffset(origin, x, y, z)))) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    /** Minimal view of a level, so matching can be unit tested without a world. */
    @FunctionalInterface
    public interface BlockGetterAccess {
        BlockState getBlockState(BlockPos pos);
    }
}
