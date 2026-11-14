package dev.microcosm.world;

import dev.microcosm.ModRegistry;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.component.BundleContents;
import net.minecraft.world.item.component.ItemContainerContents;
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
        return scale != null ? scale : Math.max(placeDepth, 0);
    }

    /** Gives the item the absolute {@code scale} while it is in a place of scale {@code placeDepth}. */
    public static ItemStack withScale(ItemStack stack, int scale, int placeDepth) {
        if (!stack.isEmpty()) {
            stack.set(ModRegistry.SCALE.get(), scale == placeDepth ? null : scale);
        }
        return stack;
    }

    /** Whether the item is somewhere that is not its own scale (it then carries the scale component). */
    public static boolean isForeign(ItemStack stack) {
        return !stack.isEmpty() && stack.get(ModRegistry.SCALE.get()) != null;
    }

    /**
     * Moves an item from a place of one scale to a place of another, keeping its absolute scale. Items stored inside
     * it (shulker boxes, bundles) move along.
     */
    public static ItemStack move(ItemStack stack, int fromDepth, int toDepth) {
        if (stack.isEmpty() || fromDepth == toDepth) {
            return stack;
        }
        withScale(stack, scaleOf(stack, fromDepth), toDepth);
        ItemContainerContents container = stack.get(DataComponents.CONTAINER);
        if (container != null) {
            List<ItemStack> contents = container.allItemsCopyStream().toList();
            contents.forEach(item -> move(item, fromDepth, toDepth));
            stack.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(contents));
        }
        BundleContents bundle = stack.get(DataComponents.BUNDLE_CONTENTS);
        if (bundle != null && !bundle.isEmpty()) {
            List<ItemStackTemplate> contents = new ArrayList<>();
            bundle.itemCopyStream().forEach(item -> contents.add(ItemStackTemplate.fromNonEmptyStack(move(item, fromDepth, toDepth))));
            stack.set(DataComponents.BUNDLE_CONTENTS, new BundleContents(contents));
        }
        return stack;
    }

    /** Stores the absolute scale on the item even at scale 0, for storage that is reachable from every scale. */
    public static ItemStack toAbsolute(ItemStack stack, int placeDepth) {
        if (!stack.isEmpty()) {
            move(stack, placeDepth, -1);
        }
        return stack;
    }

    /** The reverse of {@link #toAbsolute}: untagged items count as normal (scale 0) items. */
    public static ItemStack fromAbsolute(ItemStack stack, int placeDepth) {
        if (!stack.isEmpty()) {
            move(stack, -1, placeDepth);
        }
        return stack;
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
