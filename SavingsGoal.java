import java.io.Serializable;
import java.math.BigDecimal;
import java.math.RoundingMode;

/** Progress uses the wallet's current balance; withdrawals reduce progress. */
public final class SavingsGoal implements Serializable {
    private static final long serialVersionUID = 1L;
    private final String name;
    private final BigDecimal target;

    public SavingsGoal(String name, BigDecimal target) {
        if (name == null || name.isBlank()) throw new IllegalArgumentException("Goal name is required.");
        this.name = name.trim();
        this.target = Money.positive(target);
    }

    public String getName() { return name; }
    public BigDecimal getTarget() { return target; }
    public boolean isReached(BigDecimal balance) { return balance.compareTo(target) >= 0; }
    public BigDecimal progressPercent(BigDecimal balance) {
        return balance.multiply(BigDecimal.valueOf(100)).divide(target, 2, RoundingMode.HALF_UP)
                .max(BigDecimal.ZERO).min(new BigDecimal("100.00"));
    }
}
