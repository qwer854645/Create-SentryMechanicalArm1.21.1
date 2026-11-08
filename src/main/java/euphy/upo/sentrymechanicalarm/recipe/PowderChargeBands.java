package euphy.upo.sentrymechanicalarm.recipe;

import java.math.BigDecimal;
import java.util.List;

/** Inclusive upper bounds in native gunpowder per round; charge includes the primer portion. */
public final class PowderChargeBands {
    private PowderChargeBands() {}
    public static boolean validEntry(Object value) {
        if (!(value instanceof String rule)) return false;
        try {
            var parts = rule.split("=", -1);
            if (parts.length != 2) return false;
            int count = Integer.parseInt(parts[1].trim());
            return count >= 1 && count <= 64 && (parts[0].trim().equals("*")
                    || new BigDecimal(parts[0].trim()).signum() > 0);
        } catch (RuntimeException error) { return false; }
    }
    public static int portions(BigDecimal powder, int rounds, List<? extends String> rules) {
        if (powder.signum() <= 0 || rounds <= 0 || rules.isEmpty()) throw new IllegalArgumentException("Missing powder or bands");
        BigDecimal previous = BigDecimal.ZERO;
        int selected = 0;
        boolean terminal = false;
        for (int i = 0; i < rules.size(); i++) {
            String rule = rules.get(i);
            if (!validEntry(rule)) throw new IllegalArgumentException("Invalid powder band: " + rule);
            var parts = rule.split("=", -1);
            int count = Integer.parseInt(parts[1].trim());
            if (parts[0].trim().equals("*")) {
                if (i != rules.size() - 1) throw new IllegalArgumentException("Wildcard must be last");
                terminal = true;
                if (selected == 0) selected = count;
            } else {
                var bound = new BigDecimal(parts[0].trim());
                if (bound.compareTo(previous) <= 0) throw new IllegalArgumentException("Powder bounds must increase");
                previous = bound;
                // Compare before dividing: exact endpoints do not depend on floating point rounding.
                if (selected == 0 && powder.compareTo(bound.multiply(BigDecimal.valueOf(rounds))) <= 0) selected = count;
            }
        }
        if (!terminal) throw new IllegalArgumentException("Powder bands require a final wildcard");
        return selected;
    }
}
