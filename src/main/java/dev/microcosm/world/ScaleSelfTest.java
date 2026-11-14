package dev.microcosm.world;

import java.util.ArrayList;
import java.util.List;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.energy.SimpleEnergyHandler;
import net.neoforged.neoforge.transfer.transaction.Transaction;

/** Checks the scale conversion of fluids and energy through ports (run with {@code /microcosm selftest}). */
public final class ScaleSelfTest {
    private ScaleSelfTest() {}

    /** @return one line per check, starting with PASS or FAIL */
    public static List<String> run() {
        List<String> out = new ArrayList<>();
        int r = ScaledAmounts.RATIO;

        // Outside pushes 5 FE into a room: the room receives 5 * 4096.
        SimpleEnergyHandler inside = new SimpleEnergyHandler(1_000_000, 1_000_000, 1_000_000, 0);
        EnergyHandler intoRoom = new ScaledEnergyHandler(inside, 0, 1);
        check(out, "energy_in_scaled", commit(t -> intoRoom.insert(5, t)) == 5 && inside.getAmountAsInt() == 5 * r);

        // Outside pulls from a room holding 5000 small units: only one whole FE (4096 small units) moves.
        inside.set(5000);
        check(out, "energy_out_whole_units", commit(t -> intoRoom.extract(10, t)) == 1 && inside.getAmountAsInt() == 5000 - r);

        // Less than one whole unit never moves and is never lost.
        inside.set(1000);
        check(out, "energy_out_no_rounding", commit(t -> intoRoom.extract(1, t)) == 0 && inside.getAmountAsInt() == 1000);

        // Inside pushes 5000 small units out: one FE arrives, exactly 4096 are taken.
        SimpleEnergyHandler outside = new SimpleEnergyHandler(1_000_000, 1_000_000, 1_000_000, 0);
        EnergyHandler outOfRoom = new ScaledEnergyHandler(outside, 1, 0);
        check(out, "energy_push_out_charges_exactly", commit(t -> outOfRoom.insert(5000, t)) == r && outside.getAmountAsInt() == 1);

        // The room cannot take a full unit: nothing moves.
        SimpleEnergyHandler almostFull = new SimpleEnergyHandler(10_000, 10_000, 10_000, 9_000);
        EnergyHandler intoFullRoom = new ScaledEnergyHandler(almostFull, 0, 1);
        check(out, "energy_in_no_partial_units", commit(t -> intoFullRoom.insert(3, t)) == 0 && almostFull.getAmountAsInt() == 9_000);
        return out;
    }

    private interface Op {
        int apply(Transaction transaction);
    }

    private static int commit(Op op) {
        try (Transaction transaction = Transaction.openRoot()) {
            int result = op.apply(transaction);
            transaction.commit();
            return result;
        }
    }

    private static void check(List<String> out, String name, boolean ok) {
        out.add((ok ? "PASS " : "FAIL ") + name);
    }
}
