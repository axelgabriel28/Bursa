import java.io.Serializable;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.Objects;

/** A wallet-wide expense limit for one calendar month. Transfers do not count. */
public final class MonthlyBudget implements Serializable {
    private static final long serialVersionUID = 1L;
    private final YearMonth month;
    private final BigDecimal limit;

    public MonthlyBudget(YearMonth month, BigDecimal limit) {
        this.month = Objects.requireNonNull(month);
        this.limit = Money.positive(limit);
    }

    public YearMonth getMonth() { return month; }
    public BigDecimal getLimit() { return limit; }
    public BigDecimal remaining(BigDecimal spent) { return limit.subtract(spent); }
}
