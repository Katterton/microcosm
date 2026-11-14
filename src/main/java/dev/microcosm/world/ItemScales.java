package dev.microcosm.world;

import dev.microcosm.ModRegistry;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.transfer.item.ItemResource;

/**
 * Items have a scale, like blocks: a gold block from a Milliblock room is a Milliblock gold block (1/4096 of a
 * normal one) wherever it goes. Items without the scale component belong to the scale of the place they are in;
 * the component is only set while an item is somewhere else, so ordinary items stay untouched.
 */
public final class ItemScales {
    /** One block of a scale is this many blocks of the next smaller scale. */
    public static final int PER_BLOCK = 16 * 16 * 16;

    private ItemScales() {}

    /** The item's absolute scale (0 = normal blocks), given the scale of the place it is in. */
    public static int scaleOf(ItemStack stack, int placeDepth) {
        Integer scale = stack.get(ModRegistry.SCALE.get());
        return scale != null ? scale : placeDepth;
    }

    /** Gives the item the absolute {@code scale} while it is in a place of scale {@code placeDepth}. */
    public static ItemStack withScale(ItemStack stack, int scale, int placeDepth) {
        if (!stack.isEmpty()) {
            stack.set(ModRegistry.SCALE.get(), scale == placeDepth ? null : scale);
        }
        return stack;
    }

    /** Moves an item from a place of one scale to a place of another, keeping its absolute scale. */
    public static ItemStack move(ItemStack stack, int fromDepth, int toDepth) {
        return stack.isEmpty() ? stack : withScale(stack, scaleOf(stack, fromDepth), toDepth);
    }

    public static ItemResource move(ItemResource resource, int fromDepth, int toDepth) {
        if (resource.isEmpty()) {
            return resource;
        }
        Integer scale = resource.get(ModRegistry.SCALE.get());
        int absolute = scale != null ? scale : fromDepth;
        return resource.with(ModRegistry.SCALE.get(), absolute == toDepth ? null : absolute);
    }

    /** Whether a block item may be placed in a place of scale {@code placeDepth}. */
    public static boolean fits(ItemStack stack, int placeDepth) {
        return scaleOf(stack, placeDepth) == placeDepth;
    }
}
