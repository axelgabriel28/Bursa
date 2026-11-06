import java.io.Serializable;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class Wallet implements Serializable {
    private static final long serialVersionUID = 1L;
    private final UUID id = UUID.randomUUID();
    private final String name;
    private final WalletType type;
    private BigDecimal balance = new BigDecimal("0.00");
    private final ArrayList<Transaction> history = new ArrayList<>();
    private final LinkedHashMap<YearMonth, MonthlyBudget> budgets = new LinkedHashMap<>();
    private SavingsGoal savingsGoal;

    Wallet(String name, WalletType type) {
        if (name == null || name.isBlank()) throw new IllegalArgumentException("Wallet name is required.");
        this.name = name.trim();
        this.type = Objects.requireNonNull(type);
    }

    public UUID getId() { return id; }
    public String getName() { return name; }
    public WalletType getType() { return type; }
    public BigDecimal getBalance() { return balance; }
    public List<Transaction> getHistory() { return List.copyOf(history); }
    public Map<YearMonth, MonthlyBudget> getBudgets() { return Map.copyOf(budgets); }
    public Optional<SavingsGoal> getSavingsGoal() { return Optional.ofNullable(savingsGoal); }

    public BigDecimal expensesFor(YearMonth month, ZoneId zone) {
        return history.stream().filter(t -> t.getType() == TransactionType.EXPENSE)
                .filter(t -> YearMonth.from(t.getTimestamp().atZone(zone)).equals(month))
                .map(Transaction::getAmount).reduce(new BigDecimal("0.00"), BigDecimal::add);
    }

    void append(Transaction transaction) {
        balance = balance.add(transaction.signedAmount());
        history.add(transaction);
    }

    void setBudget(MonthlyBudget budget) { budgets.put(budget.getMonth(), budget); }
    void setSavingsGoal(SavingsGoal goal) { savingsGoal = goal; }
}
