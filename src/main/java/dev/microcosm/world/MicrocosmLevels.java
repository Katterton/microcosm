package dev.microcosm.world;

import dev.microcosm.Microcosm;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

/**
 * The scale levels. Depth 0 is any normal dimension; deeper levels live in a pool of dimensions
 * ({@code microcosm:pool_<n>}) that are handed out to tiles as needed. Which pool dimension is at which depth is
 * kept here, on the server and (synced) on clients.
 */
public final class MicrocosmLevels {
    public static final int MAX_DEPTH = 3;
    private static final String POOL_PREFIX = "pool_";

    private static final Map<ResourceKey<Level>, Integer> DEPTHS = new ConcurrentHashMap<>();

    private MicrocosmLevels() {}

    public static ResourceKey<Level> poolKey(int slot) {
        return ResourceKey.create(Registries.DIMENSION, Microcosm.id(POOL_PREFIX + slot));
    }

    /** The pool slot of a pool dimension, or -1. */
    public static int poolSlot(ResourceKey<Level> dimension) {
        Identifier id = dimension.identifier();
        if (!id.getNamespace().equals(Microcosm.MODID) || !id.getPath().startsWith(POOL_PREFIX)) {
            return -1;
        }
        try {
            return Integer.parseInt(id.getPath().substring(POOL_PREFIX.length()));
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    /** 0 for normal dimensions (and unused pool dimensions), 1..MAX_DEPTH for rooms. */
    public static int depth(ResourceKey<Level> dimension) {
        return DEPTHS.getOrDefault(dimension, 0);
    }

    public static int depth(Level level) {
        return depth(level.dimension());
    }

    static void setDepth(ResourceKey<Level> dimension, int depth) {
        DEPTHS.put(dimension, depth);
    }

    /** Replaces all known depths (server start, or a sync from the server). */
    public static void setAll(Map<ResourceKey<Level>, Integer> depths) {
        DEPTHS.clear();
        DEPTHS.putAll(depths);
    }

    public static Map<ResourceKey<Level>, Integer> all() {
        return Map.copyOf(DEPTHS);
    }

    /** The scale's name with its unit, e.g. "Milliblock (mBk)"; depth 0 is the normal "Block". */
    public static Component scaleName(int depth) {
        return Component.translatable("scale.microcosm." + Math.max(0, Math.min(MAX_DEPTH, depth)));
    }

    /** Just the unit: Bk, mBk, µBk or nBk. */
    public static Component scaleUnit(int depth) {
        return Component.translatable("scale.microcosm." + Math.max(0, Math.min(MAX_DEPTH, depth)) + ".unit");
    }
}
