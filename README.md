# Bursa

A Localized Multi-Ledger Financial Management System: an offline Java CLI for income,
expenses, transfers, monthly budgets, and savings goals. Uses only the Java standard
library. Requires JDK 17 or newer; no Maven, database, or network connection is needed.

## Compile and run

From the repository root in PowerShell:

```powershell
javac *.java
java Main
```

The default data file is `bursa.dat`, relative to the working directory. You
can supply a different location:

```powershell
java Main "my-ledger.dat"
```

Choose **2** to create a wallet, selecting `PERSONAL`, `ORGANIZATION`, `SAVINGS`, or
`E_WALLET`. Choose **1** to list numbered wallets and balances. Choose **3** to record
an income or expense, **4** to transfer funds, and **5** to see a wallet's balance,
history, budgets, and savings goal. To check an Organization Wallet, choose **5**
and enter its pouch number. Options **6** and **7** set budgets and goals. Choose **0** to
save and exit. End-of-input also saves. Invalid input returns to the menu.

## Structure

| Class or interface | Responsibility |
| --- | --- |
| `Wallet`, `WalletType` | Distinct identities, categories, balances, histories, budgets, and goals |
| `Transaction`, `TransactionType` | Immutable timestamped income, expense, and transfer entries |
| `LedgerService` | Validated, synchronized wallet operations and paired transfer logging |
| `MonthlyBudget` | Wallet-wide monthly expense limit and remaining amount |
| `SavingsGoal` | Named target and progress based on current wallet balance |
| `LedgerStorage` | Interface for saving and loading the ledger |
| `StorageManager` | Versioned object serialization, session locking, and atomic snapshot saves |
| `Main` | Interactive terminal input and startup/exit persistence |

All Java source files are in the project root; no source or build folders are required. Wallet categories use one encapsulated
class with an enum because their balance behavior is identical. The service owns
mutations; callers receive immutable transaction entries and collection snapshots.
The service serializes mutations and saves. The CLI is single-threaded; callers
building a concurrent UI should also read wallet details under the service monitor.

## Business rules

- All wallets use **PHP**, with exact `BigDecimal` arithmetic and at most two
  decimal places. Enter positive amounts; the entry type controls the direction.
  Wallets start at zero; record initial funds as income so the history explains
  the balance. Expenses and transfers cannot overdraw a wallet.
- Transfers require two existing, different wallets and sufficient funds. Both
  entries are created before mutation and share a transfer UUID, timestamp, and
  amount, with reciprocal wallet references. They preserve the combined balance.
- Each wallet can have one budget per `YYYY-MM`, and setting it again replaces
  that month's limit. Only expenses count; income and transfers are excluded.
  Negative remaining amounts indicate overspending. Budgets are advisory.
- Month boundaries use the ledger's persisted `Asia/Manila` time zone. Transaction
  timestamps are stored as UTC `Instant` values and displayed in UTC.
- Each wallet can have one active savings goal; setting another replaces it.
  All funds in that wallet count toward the goal. Withdrawals reduce progress;
  displayed progress is capped at 100%. Goals do not reserve or restrict funds.

## Example service usage

```java
import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.YearMonth;

// Inside a method:
try (LedgerStorage storage = new StorageManager(Path.of("example.dat"))) {
    LedgerService ledger = storage.load();
    Wallet personal = ledger.createWallet("Daily spending", WalletType.PERSONAL);
    Wallet organization = ledger.createWallet("Student club", WalletType.ORGANIZATION);
    Wallet savings = ledger.createWallet("Emergency fund", WalletType.SAVINGS);

    ledger.addTransaction(personal.getId(), TransactionType.INCOME,
            new BigDecimal("10000.00"), "Salary");
    ledger.addTransaction(personal.getId(), TransactionType.EXPENSE,
            new BigDecimal("150.00"), "Food");
    ledger.transfer(personal.getId(), organization.getId(), new BigDecimal("500.00"));
    ledger.setBudget(personal.getId(), YearMonth.now(ledger.getZone()),
            new BigDecimal("3000.00"));
    ledger.setSavingsGoal(savings.getId(), "Emergency fund", new BigDecimal("20000.00"));
    ledger.transfer(personal.getId(), savings.getId(), new BigDecimal("1000.00"));
    System.out.println(organization.getBalance()); // 500.00
    storage.save(ledger);
}
```

This example creates new wallets each time it runs. Existing wallets can be
retrieved by UUID with `ledger.getWallet(id)`.

## Persistence behavior

The CLI holds an operating-system file lock for its entire session, preventing two
cooperating Bursa sessions from overwriting each other's changes. A save writes a
temporary snapshot in the same directory, flushes it to disk, and atomically
replaces the previous file. If atomic replacement is unsupported, saving fails
and preserves the previous snapshot. Loading rejects oversized files and uses a
deserialization allowlist and resource limits. Corrupt or incompatible files stop
startup without automatically resetting or overwriting the data.

Data is saved on normal exit or EOF. An abrupt process termination before saving
loses that session's changes. Atomic replacement protects against partial-file
writes; it is not a backup or a guarantee against hardware failure. The snapshot
format is versioned `BURSA-1`; future model changes may require a migration.
Files are not encrypted or authenticated: use trusted local files and OS access
controls, and keep backups for important records. This is a local single-user
system; it does not implement user authentication or currency conversion.

## Run regression checks

```powershell
javac --release 17 -Xlint:all *.java
java LedgerTest
```

The dependency-free checks cover wallet isolation, transfer pairing and rejected
operations, immutable histories, monthly budgets and time zones, savings progress,
storage round trips, snapshot replacement, session locking, and corrupt-file
preservation. Tests use temporary files and clean them up afterward.

## Console adventure theme

The console uses original ASCII turnip artwork and a vegetable-adventure theme inspired by Turnip Boy Commits Tax Evasion. Framed village menus, numbered pouch selection, a treasury display, and savings quest progress bars keep finance operations readable. All sources stay in the project root.

Interactive terminals use sage green, cream, and warm gold ANSI colors. Set the NO_COLOR environment variable to disable color. Redirected output is automatically plain text. Choose wallet types with numbers 1 through 4; wallet selection accepts the displayed number or an existing UUID.
