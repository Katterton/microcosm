package dev.microcosm.world;

import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;

/** A fluid handler seen through a port: a bucket at one scale is 4096 buckets one scale smaller. */
public final class ScaledFluidHandler implements ResourceHandler<FluidResource> {
    private final ResourceHandler<FluidResource> delegate;
    private final boolean ownerSmaller;

    public ScaledFluidHandler(ResourceHandler<FluidResource> delegate, int callerDepth, int ownerDepth) {
        this.delegate = delegate;
        this.ownerSmaller = ownerDepth > callerDepth;
    }

    @Override
    public int size() {
        return delegate.size();
    }

    @Override
    public FluidResource getResource(int index) {
        return delegate.getResource(index);
    }

    @Override
    public long getAmountAsLong(int index) {
        return ScaledAmounts.toCaller(delegate.getAmountAsLong(index), ownerSmaller);
    }

    @Override
    public long getCapacityAsLong(int index, FluidResource resource) {
        return ScaledAmounts.toCaller(delegate.getCapacityAsLong(index, resource), ownerSmaller);
    }

    @Override
    public boolean isValid(int index, FluidResource resource) {
        return delegate.isValid(index, resource);
    }

    @Override
    public int insert(int index, FluidResource resource, int amount, TransactionContext transaction) {
        return ScaledAmounts.transfer(amount, ownerSmaller, (a, tx) -> delegate.insert(index, resource, a, tx), transaction);
    }

    @Override
    public int extract(int index, FluidResource resource, int amount, TransactionContext transaction) {
        return ScaledAmounts.transfer(amount, ownerSmaller, (a, tx) -> delegate.extract(index, resource, a, tx), transaction);
    }
}
