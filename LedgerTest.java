import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

/** Dependency-free regression tests; checks run even without the -ea flag. */
public final class LedgerTest {
    public static void main(String[] args) throws Exception {
        LedgerService ledger = new LedgerService(ZoneId.of("Asia/Manila"));
        Wallet personal = ledger.createWallet("Daily", WalletType.PERSONAL);
        Wallet organization = ledger.createWallet("Club", WalletType.ORGANIZATION);
        Wallet savings = ledger.createWallet("Emergency", WalletType.SAVINGS);
        Wallet electronic = ledger.createWallet("Mobile", WalletType.E_WALLET);
        ledger.addTransaction(personal.getId(), TransactionType.INCOME, money("1000"), "Salary");
        ledger.addTransaction(personal.getId(), TransactionType.EXPENSE, money("125.50"), "Food");
        UUID transferId = ledger.transfer(personal.getId(), organization.getId(), money("200"));
        equal(money("674.50"), personal.getBalance(), "Source balance");
        equal(money("200.00"), organization.getBalance(), "Destination balance");
        equal(money("0.00"), electronic.getBalance(), "Separate e-wallet balance");
        Transaction debit = personal.getHistory().get(2);
        Transaction credit = organization.getHistory().get(0);
        equal(transferId, debit.getTransferId(), "Debit link");
        equal(transferId, credit.getTransferId(), "Credit link");
        equal(debit.getTimestamp(), credit.getTimestamp(), "Transfer timestamp");
        equal(debit.getAmount(), credit.getAmount(), "Transfer amounts");
        equal(organization.getId(), debit.getCounterpartyWalletId(), "Debit counterparty");
        equal(personal.getId(), credit.getCounterpartyWalletId(), "Credit counterparty");
        equal(TransactionType.TRANSFER_OUT, debit.getType(), "Debit direction");
        equal(TransactionType.TRANSFER_IN, credit.getType(), "Credit direction");

        List<Transaction> before = personal.getHistory();
        invalid(() -> ledger.transfer(personal.getId(), organization.getId(), money("10000")));
        invalid(() -> ledger.transfer(personal.getId(), personal.getId(), money("1")));
        invalid(() -> ledger.transfer(personal.getId(), UUID.randomUUID(), money("1")));
        for (String value : List.of("-1", "0", "1.001")) {
            invalid(() -> ledger.transfer(personal.getId(), organization.getId(), new BigDecimal(value)));
        }
        invalid(() -> ledger.addTransaction(personal.getId(), TransactionType.EXPENSE, money("9999"), "Food"));
        invalid(() -> ledger.addTransaction(personal.getId(), TransactionType.INCOME, money("1"), " "));
        invalid(() -> ledger.addTransaction(personal.getId(), TransactionType.TRANSFER_IN, money("1"), "Invalid"));
        equal(before, personal.getHistory(), "Rejected operations leave history unchanged");
        equal(money("674.50"), personal.getBalance(), "Rejected operations leave balance unchanged");
        equal(1, organization.getHistory().size(), "Rejected operations leave destination unchanged");
        try {
            personal.getHistory().clear();
            throw new AssertionError("History must be immutable to callers");
        } catch (UnsupportedOperationException expected) { }

        YearMonth month = YearMonth.from(debit.getTimestamp().atZone(ledger.getZone()));
        ledger.setBudget(personal.getId(), month, money("100"));
        equal(money("125.50"), personal.expensesFor(month, ledger.getZone()), "Transfers excluded from budget");
        equal(money("-25.50"), personal.getBudgets().get(month).remaining(
                personal.expensesFor(month, ledger.getZone())), "Over-budget tracking");
        equal(money("0.00"), personal.expensesFor(month.minusMonths(1), ledger.getZone()), "Separate months");
        // UTC January 31 is February 1 locally: month boundaries use the ledger's zone.
        Wallet boundary = new Wallet("Boundary", WalletType.PERSONAL);
        boundary.append(new Transaction(Instant.parse("2026-01-31T16:00:00Z"),
                TransactionType.EXPENSE, money("1"), "Food", null, null));
        equal(money("1.00"), boundary.expensesFor(YearMonth.of(2026, 2), ledger.getZone()), "Local month boundary");
        ledger.setSavingsGoal(savings.getId(), "Emergency fund", money("500"));
        ledger.transfer(personal.getId(), savings.getId(), money("250"));
        SavingsGoal goal = savings.getSavingsGoal().orElseThrow();
        equal(money("50.00"), goal.progressPercent(savings.getBalance()), "Goal progress");
        ledger.transfer(personal.getId(), savings.getId(), money("250"));
        check(goal.isReached(savings.getBalance()), "Goal reached");
        ledger.addTransaction(savings.getId(), TransactionType.EXPENSE, money("50"), "Emergency");
        equal(money("90.00"), goal.progressPercent(savings.getBalance()), "Withdrawal reduces progress");

        Path directory = Files.createTempDirectory("bursa-test-");
        Path data = directory.resolve("ledger.dat");
        try {
            try (StorageManager storage = new StorageManager(data)) {
                equal(0, storage.load().getWallets().size(), "Missing file starts empty");
                try (StorageManager ignored = new StorageManager(data)) {
                    ignored.load();
                    throw new AssertionError("Second session should fail");
                } catch (IOException expected) { }
                storage.save(ledger);
                storage.save(ledger); // Exercise replacement of an existing snapshot.
            }
            try (StorageManager storage = new StorageManager(data)) {
                LedgerService restored = storage.load();
                equal(4, restored.getWallets().size(), "Wallet round trip");
                equal(personal.getBalance(), restored.getWallet(personal.getId()).getBalance(), "Balance round trip");
                equal(transferId, restored.getWallet(organization.getId()).getHistory().get(0).getTransferId(), "History round trip");
                equal(money("100.00"), restored.getWallet(personal.getId()).getBudgets().get(month).getLimit(), "Budget round trip");
                equal("Emergency fund", restored.getWallet(savings.getId()).getSavingsGoal().orElseThrow().getName(), "Goal round trip");
                equal(ledger.getZone(), restored.getZone(), "Zone round trip");
            }
            Files.writeString(data, "damaged file");
            try (StorageManager storage = new StorageManager(data)) {
                try {
                    storage.load();
                    throw new AssertionError("Corrupt data should fail");
                } catch (IOException expected) { }
            }
            equal("damaged file", Files.readString(data), "Corrupt data preserved");
        } finally {
            Files.deleteIfExists(data);
            Files.deleteIfExists(directory.resolve("ledger.dat.lock"));
            Files.deleteIfExists(directory);
        }
        System.out.println("All Bursa regression checks passed.");
    }

    private static BigDecimal money(String value) { return new BigDecimal(value).setScale(2, java.math.RoundingMode.UNNECESSARY); }
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
    private static void equal(Object expected, Object actual, String message) {
        check(expected.equals(actual), message + ": expected " + expected + ", got " + actual);
    }
    private static void invalid(Runnable action) {
        try { action.run(); } catch (IllegalArgumentException expected) { return; }
        throw new AssertionError("Expected validation failure");
    }
}
