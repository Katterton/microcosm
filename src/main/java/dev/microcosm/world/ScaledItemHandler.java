package dev.microcosm.world;

import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;

/**
 * An item handler seen across a scale boundary (through a port): items keep their absolute scale, so what goes in
 * or comes out is tagged for the side it ends up on.
 */
public final class ScaledItemHandler implements ResourceHandler<ItemResource> {
    private final ResourceHandler<ItemResource> delegate;
    /** Scale of the side that uses this handler. */
    private final int callerDepth;
    /** Scale of the side the handler belongs to. */
    private final int ownerDepth;

    public ScaledItemHandler(ResourceHandler<ItemResource> delegate, int callerDepth, int ownerDepth) {
        this.delegate = delegate;
        this.callerDepth = callerDepth;
        this.ownerDepth = ownerDepth;
    }

    private ItemResource toOwner(ItemResource resource) {
        return ItemScales.move(resource, callerDepth, ownerDepth);
    }

    private ItemResource toCaller(ItemResource resource) {
        return ItemScales.move(resource, ownerDepth, callerDepth);
    }

    @Override
    public int size() {
        return delegate.size();
    }

    @Override
    public ItemResource getResource(int index) {
        return toCaller(delegate.getResource(index));
    }

    @Override
    public long getAmountAsLong(int index) {
        return delegate.getAmountAsLong(index);
    }

    @Override
    public long getCapacityAsLong(int index, ItemResource resource) {
        return delegate.getCapacityAsLong(index, toOwner(resource));
    }

    @Override
    public boolean isValid(int index, ItemResource resource) {
        return delegate.isValid(index, toOwner(resource));
    }

    @Override
    public int insert(int index, ItemResource resource, int amount, TransactionContext transaction) {
        return delegate.insert(index, toOwner(resource), amount, transaction);
    }

    @Override
    public int extract(int index, ItemResource resource, int amount, TransactionContext transaction) {
        return delegate.extract(index, toOwner(resource), amount, transaction);
    }
}
