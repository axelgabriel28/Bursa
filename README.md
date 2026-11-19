# Bursa

Bursa is an offline Java command-line program for managing separate wallets in Philippine pesos (PHP). It records income and expenses, moves money between wallets, tracks monthly spending, and measures progress toward savings goals. The console calls wallets **pouches** and shows their combined balance as the **treasury**.

## Group members

| Member | Name |
| --- | --- |
| 1 | [Member 1 name] |
| 2 | [Member 2 name] |
| 3 | [Member 3 name] |
| 4 | [Member 4 name] |

## Compile and run

Install JDK 17 or newer and make sure both `java -version` and `javac -version` report version 17 or newer. Bursa uses the Java standard library and stores its data locally.

From the repository root in PowerShell, run:

```powershell
javac --release 17 *.java
java Main
```

Bursa uses `bursa.dat` in the working directory by default. To open or create a different ledger, run `java Main "my-ledger.dat"`.

## Use the ledger

| Option | What it does |
| --- | --- |
| **1** | List wallets and their current balances. |
| **2** | Create a Personal, Organization, Savings, or E-Wallet pouch. |
| **3** | Record income or an expense with an amount and category. |
| **4** | Transfer funds between two wallets. |
| **5** | View a wallet's balance, transaction history, budgets, and savings goal. |
| **6** | Set or replace a spending limit for a month in `YYYY-MM` format. |
| **7** | Set or replace a wallet's savings goal. |
| **0** | Save the ledger and exit. |

Create a wallet with **2**, then use **3** to record its starting funds as `INCOME`. New wallets start at PHP 0.00. When prompted for a wallet, enter its displayed number or its UUID. Set the `NO_COLOR` environment variable to disable console colors.

- **Amounts and transfers.** Enter positive amounts with up to two decimal places. Bursa uses `BigDecimal` for exact decimal arithmetic and rejects expenses or transfers that would overdraw a wallet. A transfer creates an outgoing and an incoming entry with the same transfer ID, amount, and timestamp.
- **Monthly budgets.** Only expenses count toward the limit. Bursa groups them by month using the ledger's `Asia/Manila` time zone by default. A negative remaining amount shows overspending. The limit is advisory.
- **Savings goals.** Each wallet has one active goal. Progress uses the current wallet balance and is displayed up to 100%. An expense or outgoing transfer can reduce progress.
- **Local storage.** Bursa loads the ledger on startup and saves on normal exit or end-of-input. `StorageManager` locks the data file for the session through a companion lock file, then saves by atomically replacing the previous snapshot. A corrupt or incompatible file stops startup and is preserved. The snapshot is unencrypted.

## Sample console demo

After compiling, paste the following into PowerShell. Each quoted value is one response to a Bursa prompt. The demo uses a fresh file in your temporary directory, so wallet numbers start at 1.

```powershell
$demoFile = Join-Path ([IO.Path]::GetTempPath()) "bursa-demo-$([guid]::NewGuid()).dat"
$month = [DateTime]::UtcNow.AddHours(8).ToString('yyyy-MM') # Current month in Manila

$demoInput = @(
    '2', 'Daily spending', '1'           # Create wallet 1 (Personal)
    '2', 'Emergency fund', '3'           # Create wallet 2 (Savings)
    '3', '1', 'INCOME', '1000', 'Salary' # Record income in wallet 1
    '3', '1', 'EXPENSE', '125.50', 'Food'
    '4', '1', '2', '250'                 # Transfer from wallet 1 to wallet 2
    '6', '1', $month, '300'              # Set wallet 1's monthly budget
    '7', '2', 'Emergency fund', '500'    # Set wallet 2's savings goal
    '5', '1'                            # Inspect wallet 1
    '5', '2'                            # Inspect wallet 2
    '0'                                 # Save and exit
)

$demoInput | java Main $demoFile
```

| What to look for | Expected result |
| --- | --- |
| Daily spending balance | PHP 624.50 |
| Daily spending budget | PHP 125.50 spent, PHP 174.50 remaining |
| Emergency fund balance | PHP 250.00 |
| Emergency fund goal | 50.00% of PHP 500.00 |

To reopen the saved demo, run `java Main $demoFile` in the same PowerShell session.


## How Bursa uses the four pillars of OOP

### Encapsulation

[Wallet.java](Wallet.java) keeps the balance, history, budgets, and savings goal in private fields. `getHistory()` and `getBudgets()` return unmodifiable snapshots, so callers cannot edit those collections directly. In the normal program flow, [LedgerService.java](LedgerService.java) validates operations before calling the wallet's package-private methods. For example, `transfer()` checks both wallets and the available funds before it records either entry.

### Abstraction

[LedgerStorage.java](LedgerStorage.java) defines the persistence operations `load()`, `save()`, and `close()`. Code that uses this interface can work with a ledger without handling file locks, Java serialization, or snapshot replacement. [StorageManager.java](StorageManager.java) handles those details. The console follows the same principle when it calls `LedgerService.transfer()` to move funds through one operation.

### Inheritance

`LedgerStorage` uses `extends AutoCloseable` to inherit Java's resource-closing contract. `StorageManager` implements that interface and supplies `close()`, which releases the session lock and closes its file channel. [Main.java](Main.java) uses try-with-resources so Java calls `close()` when the session ends. The four wallet categories are values of the [WalletType.java](WalletType.java) enum and share the same `Wallet` class.

### Polymorphism

An interface reference such as `LedgerStorage storage = new StorageManager(file)` can call `storage.save(ledger)`, and Java dispatches the call to the `StorageManager` implementation. Bursa also uses method overriding in [Transaction.java](Transaction.java). When `Main` prints a transaction through `System.out::println`, Java uses `Transaction.toString()` to show the timestamp, type, amount, category, and any transfer references.

## Source layout

| Source | Responsibility |
| --- | --- |
| [Main.java](Main.java), [ConsoleUI.java](ConsoleUI.java) | Read input, display the themed menu, and present wallet details. |
| [LedgerService.java](LedgerService.java) | Create wallets and validate transactions, transfers, budgets, and goals. |
| [Wallet.java](Wallet.java), [WalletType.java](WalletType.java) | Track each wallet's identity, category, balance, history, budget limits, and goal. |
| [Transaction.java](Transaction.java), [TransactionType.java](TransactionType.java), [Money.java](Money.java) | Represent ledger entries and validate monetary amounts. |
| [MonthlyBudget.java](MonthlyBudget.java), [SavingsGoal.java](SavingsGoal.java) | Calculate the remaining budget and progress toward a savings target. |
| [LedgerStorage.java](LedgerStorage.java), [StorageManager.java](StorageManager.java) | Define the persistence contract and implement local file storage. |

## Run the regression checks

```powershell
javac --release 17 -Xlint:all *.java
java LedgerTest
```

[LedgerTest.java](LedgerTest.java) checks wallet balances, linked transfers, rejected transactions, monthly budget boundaries, savings progress, and storage behavior.
