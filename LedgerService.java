import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Single mutation boundary. Synchronized operations serialize balance changes and saves. */
public final class LedgerService implements Serializable {
    private static final long serialVersionUID = 1L;
    private final LinkedHashMap<UUID, Wallet> wallets = new LinkedHashMap<>();
    private final ZoneId zone;

    public LedgerService(ZoneId zone) { this.zone = Objects.requireNonNull(zone); }
    public ZoneId getZone() { return zone; }

    public synchronized Wallet createWallet(String name, WalletType type) {
        Wallet wallet = new Wallet(name, type);
        wallets.put(wallet.getId(), wallet);
        return wallet;
    }

    public synchronized List<Wallet> getWallets() { return List.copyOf(wallets.values()); }

    public synchronized Wallet getWallet(UUID id) {
        Wallet wallet = wallets.get(id);
        if (wallet == null) throw new IllegalArgumentException("Wallet not found: " + id);
        return wallet;
    }

    public synchronized Transaction addTransaction(UUID walletId, TransactionType type,
                                                    BigDecimal amount, String category) {
        if (type != TransactionType.INCOME && type != TransactionType.EXPENSE) {
            throw new IllegalArgumentException("Use transfer() for inter-wallet transfers.");
        }
        Wallet wallet = getWallet(walletId);
        Transaction entry = new Transaction(Instant.now(), type, amount, category, null, null);
        if (type == TransactionType.EXPENSE) requireFunds(wallet, entry.getAmount());
        wallet.append(entry);
        return entry;
    }

    /** Validates both wallets and constructs both entries before changing either balance. */
    public synchronized UUID transfer(UUID sourceId, UUID destinationId, BigDecimal amount) {
        Wallet source = getWallet(sourceId);
        Wallet destination = getWallet(destinationId);
        if (sourceId.equals(destinationId)) throw new IllegalArgumentException("Choose two different wallets.");
        BigDecimal value = Money.positive(amount);
        requireFunds(source, value);
        UUID transferId = UUID.randomUUID();
        Instant timestamp = Instant.now();
        Transaction debit = new Transaction(timestamp, TransactionType.TRANSFER_OUT,
                value, "Transfer", transferId, destinationId);
        Transaction credit = new Transaction(timestamp, TransactionType.TRANSFER_IN,
                value, "Transfer", transferId, sourceId);
        source.append(debit);
        destination.append(credit);
        return transferId;
    }

    public synchronized void setBudget(UUID walletId, YearMonth month, BigDecimal limit) {
        getWallet(walletId).setBudget(new MonthlyBudget(month, limit));
    }

    public synchronized void setSavingsGoal(UUID walletId, String name, BigDecimal target) {
        getWallet(walletId).setSavingsGoal(new SavingsGoal(name, target));
    }

    private static void requireFunds(Wallet wallet, BigDecimal amount) {
        if (wallet.getBalance().compareTo(amount) < 0) {
            throw new IllegalArgumentException("Insufficient funds in " + wallet.getName() + ".");
        }
    }
}
