package dev.microcosm.world;

import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;

/** An energy handler seen through a port: one FE at one scale is 4096 FE one scale smaller. */
public final class ScaledEnergyHandler implements EnergyHandler {
    private final EnergyHandler delegate;
    private final boolean ownerSmaller;

    public ScaledEnergyHandler(EnergyHandler delegate, int callerDepth, int ownerDepth) {
        this.delegate = delegate;
        this.ownerSmaller = ownerDepth > callerDepth;
    }

    @Override
    public long getAmountAsLong() {
        return ScaledAmounts.toCaller(delegate.getAmountAsLong(), ownerSmaller);
    }

    @Override
    public long getCapacityAsLong() {
        return ScaledAmounts.toCaller(delegate.getCapacityAsLong(), ownerSmaller);
    }

    @Override
    public int insert(int amount, TransactionContext transaction) {
        return ScaledAmounts.transfer(amount, ownerSmaller, delegate::insert, transaction);
    }

    @Override
    public int extract(int amount, TransactionContext transaction) {
        return ScaledAmounts.transfer(amount, ownerSmaller, delegate::extract, transaction);
    }
}
