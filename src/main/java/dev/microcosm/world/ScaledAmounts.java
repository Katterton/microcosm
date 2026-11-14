package dev.microcosm.world;

import net.neoforged.neoforge.transfer.transaction.Transaction;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;

/**
 * Converts fluid and energy amounts across a scale boundary: one unit at a scale equals {@link ItemScales#PER_BLOCK}
 * units one scale smaller. Only whole units of the larger scale move, so rounding can never create anything.
 */
final class ScaledAmounts {
    static final int RATIO = ItemScales.PER_BLOCK;

    /** An insert or extract on the handler being wrapped. */
    @FunctionalInterface
    interface Operation {
        int apply(int amount, TransactionContext transaction);
    }

    private ScaledAmounts() {}

    /**
     * Runs {@code operation} for {@code callerAmount} units of the caller's scale.
     *
     * @param ownerSmaller whether the wrapped handler is one scale smaller than the caller
     * @return how many caller units were moved
     */
    static int transfer(int callerAmount, boolean ownerSmaller, Operation operation, TransactionContext transaction) {
        if (callerAmount <= 0) {
            return 0;
        }
        if (ownerSmaller) {
            int ownerAmount = (int) Math.min(Integer.MAX_VALUE, (long) callerAmount * RATIO);
            int possible;
            try (Transaction simulation = Transaction.open(transaction)) {
                possible = operation.apply(ownerAmount, simulation) / RATIO;
            }
            if (possible == 0) {
                return 0;
            }
            return operation.apply(possible * RATIO, transaction) / RATIO;
        }
        int ownerAmount = callerAmount / RATIO;
        return ownerAmount == 0 ? 0 : operation.apply(ownerAmount, transaction) * RATIO;
    }

    /** An amount or capacity of the wrapped handler in caller units. */
    static long toCaller(long ownerAmount, boolean ownerSmaller) {
        return ownerSmaller ? ownerAmount / RATIO : Math.min(Long.MAX_VALUE / RATIO, ownerAmount) * RATIO;
    }
}
