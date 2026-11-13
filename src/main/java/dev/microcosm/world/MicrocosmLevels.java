package dev.microcosm.world;

import dev.microcosm.Microcosm;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

/** The nested scale levels. Depth 0 is any normal dimension, depth 1-3 are the microcosm dimensions. */
public final class MicrocosmLevels {
    public static final int MAX_DEPTH = 3;

    @SuppressWarnings("unchecked")
    private static final ResourceKey<Level>[] KEYS = new ResourceKey[MAX_DEPTH];

    static {
        for (int i = 0; i < MAX_DEPTH; i++) {
            KEYS[i] = ResourceKey.create(Registries.DIMENSION, Microcosm.id("level_" + (i + 1)));
        }
    }

    private MicrocosmLevels() {}

    public static ResourceKey<Level> key(int depth) {
        return KEYS[depth - 1];
    }

    /** 0 for normal dimensions, 1..MAX_DEPTH for microcosm levels. */
    public static int depth(ResourceKey<Level> dimension) {
        for (int i = 0; i < MAX_DEPTH; i++) {
            if (KEYS[i].equals(dimension)) {
                return i + 1;
            }
        }
        return 0;
    }

    public static int depth(Level level) {
        return depth(level.dimension());
    }

    /** The level that rooms of cells placed in {@code parent} live in, or null at the deepest level. */
    public static @Nullable ServerLevel childOf(ServerLevel parent) {
        int depth = depth(parent);
        if (depth >= MAX_DEPTH) {
            return null;
        }
        MinecraftServer server = parent.getServer();
        return server.getLevel(key(depth + 1));
    }
}
