package dev.microcosm.block;

import dev.microcosm.ModRegistry;
import dev.microcosm.world.ItemScales;
import dev.microcosm.world.MicrocosmLevels;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

/**
 * Slot 0 is the input, slot 1 the output. Shrinking turns one input item into {@link ItemScales#PER_BLOCK} items of
 * the next smaller scale, held in a buffer and handed out a stack at a time. Growing collects that many items of one
 * kind and scale and turns them into one item of the next larger scale.
 */
public class ScaleConverterBlockEntity extends BlockEntity implements WorldlyContainer {
    private static final int[] INPUT = {0};
    private static final int[] OUTPUT = {1};

    private final NonNullList<ItemStack> items = NonNullList.withSize(2, ItemStack.EMPTY);
    /** Shrunk items waiting to be handed out (one item as template, plus how many). */
    private ItemStack pending = ItemStack.EMPTY;
    private int pendingCount;
    /** Items collected for growing (one item as template, plus how many). */
    private ItemStack collected = ItemStack.EMPTY;
    private int collectedCount;

    public ScaleConverterBlockEntity(BlockPos pos, BlockState state) {
        super(ModRegistry.SCALE_CONVERTER_BLOCK_ENTITY.get(), pos, state);
    }

    void serverTick(ServerLevel level, ScaleConverterBlock.Mode mode) {
        int depth = MicrocosmLevels.depth(level);
        ItemStack input = items.get(0);
        if (mode == ScaleConverterBlock.Mode.SHRINK) {
            if (pendingCount == 0 && !input.isEmpty()) {
                int scale = ItemScales.scaleOf(input, depth);
                if (scale < MicrocosmLevels.MAX_DEPTH) {
                    pending = ItemScales.withScale(input.copyWithCount(1), scale + 1, depth);
                    pendingCount = ItemScales.PER_BLOCK;
                    input.shrink(1);
                    setChanged();
                }
            }
        } else if (!input.isEmpty()) {
            int scale = ItemScales.scaleOf(input, depth);
            if (scale > 0 && (collectedCount == 0 || ItemStack.isSameItemSameComponents(collected, input))) {
                int take = Math.min(input.getCount(), ItemScales.PER_BLOCK - collectedCount);
                collected = input.copyWithCount(1);
                collectedCount += take;
                input.shrink(take);
                setChanged();
            }
        }
        if (collectedCount >= ItemScales.PER_BLOCK) {
            int scale = ItemScales.scaleOf(collected, depth);
            ItemStack grown = ItemScales.withScale(collected.copyWithCount(1), scale - 1, depth);
            if (output(grown, 1) == 1) {
                collectedCount -= ItemScales.PER_BLOCK;
                if (collectedCount == 0) {
                    collected = ItemStack.EMPTY;
                }
            }
        }
        if (pendingCount > 0) {
            pendingCount -= output(pending, pendingCount);
            if (pendingCount == 0) {
                pending = ItemStack.EMPTY;
            }
        }
    }

    /** Moves up to {@code count} of {@code template} into the output slot; returns how many fit. */
    private int output(ItemStack template, int count) {
        ItemStack out = items.get(1);
        if (!out.isEmpty() && !ItemStack.isSameItemSameComponents(out, template)) {
            return 0;
        }
        int space = template.getMaxStackSize() - out.getCount();
        int moved = Math.min(space, count);
        if (moved > 0) {
            items.set(1, template.copyWithCount(out.getCount() + moved));
            setChanged();
        }
        return moved;
    }

    void insertByHand(ItemStack held) {
        ItemStack input = items.get(0);
        if (input.isEmpty()) {
            items.set(0, held.split(held.getCount()));
        } else if (ItemStack.isSameItemSameComponents(input, held)) {
            int moved = Math.min(held.getCount(), input.getMaxStackSize() - input.getCount());
            input.grow(moved);
            held.shrink(moved);
        }
        setChanged();
    }

    void takeOutput(Player player) {
        ItemStack out = items.get(1);
        if (!out.isEmpty()) {
            player.getInventory().placeItemBackInInventory(out);
            items.set(1, ItemStack.EMPTY);
            setChanged();
        }
    }

    void showStatus(Player player, ScaleConverterBlock.Mode mode) {
        player.sendOverlayMessage(Component.translatable("message.microcosm.converter." + mode.getSerializedName(),
                mode == ScaleConverterBlock.Mode.SHRINK ? pendingCount : collectedCount, ItemScales.PER_BLOCK));
    }

    // ---------------------------------------------------------------- container

    @Override
    public int[] getSlotsForFace(Direction side) {
        return side == Direction.DOWN ? OUTPUT : INPUT;
    }

    @Override
    public boolean canPlaceItemThroughFace(int slot, ItemStack stack, @Nullable Direction side) {
        return slot == 0;
    }

    @Override
    public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction side) {
        return slot == 1;
    }

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        return slot == 0;
    }

    @Override
    public int getContainerSize() {
        return items.size();
    }

    @Override
    public boolean isEmpty() {
        return items.stream().allMatch(ItemStack::isEmpty);
    }

    @Override
    public ItemStack getItem(int slot) {
        return items.get(slot);
    }

    @Override
    public ItemStack removeItem(int slot, int count) {
        ItemStack removed = ContainerHelper.removeItem(items, slot, count);
        setChanged();
        return removed;
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        return ContainerHelper.takeItem(items, slot);
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        items.set(slot, stack);
        setChanged();
    }

    @Override
    public boolean stillValid(Player player) {
        return true;
    }

    @Override
    public void clearContent() {
        items.clear();
    }

    /** Buffered items are not lost when the converter is broken. */
    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        super.preRemoveSideEffects(pos, state);
        if (level != null) {
            drop(pending, pendingCount, pos);
            drop(collected, collectedCount, pos);
            pendingCount = 0;
            collectedCount = 0;
        }
    }

    private void drop(ItemStack template, int count, BlockPos pos) {
        while (count > 0 && !template.isEmpty()) {
            int n = Math.min(count, template.getMaxStackSize());
            Block.popResource(level, pos, template.copyWithCount(n));
            count -= n;
        }
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        items.clear();
        ContainerHelper.loadAllItems(input, items);
        pending = input.read("pending", ItemStack.OPTIONAL_CODEC).orElse(ItemStack.EMPTY);
        pendingCount = input.getIntOr("pending_count", 0);
        collected = input.read("collected", ItemStack.OPTIONAL_CODEC).orElse(ItemStack.EMPTY);
        collectedCount = input.getIntOr("collected_count", 0);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        ContainerHelper.saveAllItems(output, items);
        output.store("pending", ItemStack.OPTIONAL_CODEC, pending);
        output.putInt("pending_count", pendingCount);
        output.store("collected", ItemStack.OPTIONAL_CODEC, collected);
        output.putInt("collected_count", collectedCount);
    }
}
