package dev.microcosm.device;

import dev.microcosm.MicrocosmConfig;
import dev.microcosm.ModRegistry;
import java.util.ArrayDeque;
import java.util.Deque;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.DispenserMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.transfer.energy.SimpleEnergyHandler;
import org.jspecify.annotations.Nullable;

/** Shared block entity of all room access devices: optional inventory, energy buffer and action queue. */
public class DeviceBlockEntity extends BaseContainerBlockEntity {
    public static final int SLOTS = 9;

    private NonNullList<ItemStack> items;
    private @Nullable SimpleEnergyHandler energy;
    private int storedEnergy;
    private final Deque<DeviceAction> queue = new ArrayDeque<>();
    private int progress;
    private DeviceAction.Result lastResult = DeviceAction.Result.DONE;

    public DeviceBlockEntity(BlockPos pos, BlockState state) {
        super(ModRegistry.DEVICE_BLOCK_ENTITY.get(), pos, state);
        this.items = NonNullList.withSize(kind().hasInventory ? SLOTS : 0, ItemStack.EMPTY);
    }

    public DeviceKind kind() {
        return getBlockState().getBlock() instanceof DeviceBlock device ? device.kind() : DeviceKind.OPTICAL_MICROSCOPE;
    }

    public MicrocosmConfig.Tier tierConfig() {
        return MicrocosmConfig.tier(kind().tier());
    }

    // ---------------------------------------------------------------- energy

    /** The energy buffer, or null if this tier needs no energy. */
    public @Nullable SimpleEnergyHandler energy() {
        if (energy == null) {
            int capacity = tierConfig().energyCapacity.getAsInt();
            if (capacity <= 0) {
                return null;
            }
            energy = new SimpleEnergyHandler(capacity, capacity, 0, Math.min(storedEnergy, capacity)) {
                @Override
                protected void onEnergyChanged(int previousAmount) {
                    setChanged();
                }
            };
        }
        return energy;
    }

    public int energyStored() {
        SimpleEnergyHandler handler = energy();
        return handler == null ? 0 : handler.getAmountAsInt();
    }

    public int energyCapacity() {
        SimpleEnergyHandler handler = energy();
        return handler == null ? 0 : handler.getCapacityAsInt();
    }

    /** Takes {@code amount} energy if available. Devices without an energy buffer always succeed. */
    public boolean consumeEnergy(int amount) {
        SimpleEnergyHandler handler = energy();
        if (handler == null || amount <= 0) {
            return true;
        }
        if (handler.getAmountAsInt() < amount) {
            return false;
        }
        handler.set(handler.getAmountAsInt() - amount);
        return true;
    }

    // ---------------------------------------------------------------- actions

    public DeviceAction.Result enqueue(DeviceAction action) {
        if (queue.size() >= MicrocosmConfig.MAX_QUEUED_ACTIONS.getAsInt()) {
            return DeviceAction.Result.QUEUE_FULL;
        }
        queue.add(action);
        return DeviceAction.Result.QUEUED;
    }

    public int queueSize() {
        return queue.size();
    }

    public DeviceAction.Result lastResult() {
        return lastResult;
    }

    void serverTick(ServerLevel level) {
        DeviceAction next = queue.peek();
        if (next == null) {
            progress = 0;
            return;
        }
        MicrocosmConfig.Tier tier = tierConfig();
        int cost = tier.actionEnergy.getAsInt();
        if (energy() != null && energyStored() < cost) {
            lastResult = DeviceAction.Result.NO_ENERGY;
            return;
        }
        if (++progress < tier.actionTicks.getAsInt()) {
            return;
        }
        progress = 0;
        queue.poll();
        consumeEnergy(cost);
        lastResult = next.execute(level, this);
        setChanged();
    }

    // ---------------------------------------------------------------- inventory

    /** Puts {@code stack} into the inventory, returning what did not fit. */
    public ItemStack insert(ItemStack stack) {
        for (int i = 0; i < items.size() && !stack.isEmpty(); i++) {
            ItemStack existing = items.get(i);
            if (!existing.isEmpty() && ItemStack.isSameItemSameComponents(existing, stack)) {
                int move = Math.min(stack.getCount(), existing.getMaxStackSize() - existing.getCount());
                existing.grow(move);
                stack.shrink(move);
            }
        }
        for (int i = 0; i < items.size() && !stack.isEmpty(); i++) {
            if (items.get(i).isEmpty()) {
                items.set(i, stack.copy());
                stack.setCount(0);
            }
        }
        setChanged();
        return stack;
    }

    @Override
    public ItemStack getItem(int slot) {
        return slot >= 0 && slot < items.size() ? items.get(slot) : ItemStack.EMPTY;
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        if (slot >= 0 && slot < items.size()) {
            super.setItem(slot, stack);
        }
    }

    @Override
    public int getContainerSize() {
        return items.size();
    }

    @Override
    protected NonNullList<ItemStack> getItems() {
        return items;
    }

    @Override
    protected void setItems(NonNullList<ItemStack> items) {
        this.items = items;
    }

    @Override
    protected Component getDefaultName() {
        return Component.translatable("block.microcosm." + kind().id);
    }

    @Override
    protected AbstractContainerMenu createMenu(int containerId, Inventory inventory) {
        return new DispenserMenu(containerId, inventory, this);
    }

    // ---------------------------------------------------------------- persistence

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        items = NonNullList.withSize(kind().hasInventory ? SLOTS : 0, ItemStack.EMPTY);
        ContainerHelper.loadAllItems(input, items);
        storedEnergy = input.getIntOr("energy", 0);
        if (energy != null) {
            energy.set(Math.min(storedEnergy, energy.getCapacityAsInt()));
        }
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        ContainerHelper.saveAllItems(output, items);
        output.putInt("energy", energy != null ? energy.getAmountAsInt() : storedEnergy);
    }
}
