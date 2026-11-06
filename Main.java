import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.NoSuchElementException;
import java.util.Scanner;
import java.util.UUID;

/** Interactive offline terminal entry point. No network services or dependencies. */
public final class Main {
    private Main() { }

    public static void main(String[] args) {
        Path file = Path.of(args.length == 0 ? "bursa.dat" : args[0]);
        try (StorageManager storage = new StorageManager(file);
             Scanner scanner = new Scanner(System.in)) {
            LedgerService ledger = storage.load();
            ConsoleUI.welcome();
            boolean running = true;
            while (running) {
                ConsoleUI.menu(ledger);
                try {
                    String choice = prompt(scanner, "  Choose your next move > ");
                    switch (choice) {
                        case "1" -> listWallets(ledger);
                        case "2" -> {
                            String name = prompt(scanner, "Wallet name: ");
                            ConsoleUI.title("CHOOSE YOUR POUCH");
                            ConsoleUI.line("[1] Personal  [2] Organization  [3] Savings  [4] E-Wallet");
                            ConsoleUI.border();
                            String selection = prompt(scanner, "  Pouch type > ").toUpperCase(java.util.Locale.ROOT);
                            WalletType type = switch (selection) {
                                case "1" -> WalletType.PERSONAL;
                                case "2" -> WalletType.ORGANIZATION;
                                case "3" -> WalletType.SAVINGS;
                                case "4" -> WalletType.E_WALLET;
                                default -> WalletType.valueOf(selection);
                            };
                            Wallet wallet = ledger.createWallet(name, type);
                            ConsoleUI.notice("New pouch ready: " + wallet.getName());
                        }
                        case "3" -> {
                            UUID wallet = walletId(scanner, ledger);
                            TransactionType type = TransactionType.valueOf(prompt(scanner,
                                    "Type (INCOME, EXPENSE): ").toUpperCase(java.util.Locale.ROOT));
                            BigDecimal amount = amount(scanner);
                            String category = prompt(scanner, "Category (e.g. Salary, Food): ");
                            ledger.addTransaction(wallet, type, amount, category);
                            System.out.println("Transaction recorded.");
                        }
                        case "4" -> {
                            System.out.println("Source:");
                            UUID source = walletId(scanner, ledger);
                            System.out.println("Destination:");
                            UUID destination = walletId(scanner, ledger);
                            UUID transfer = ledger.transfer(source, destination, amount(scanner));
                            System.out.println("Transfer recorded: " + transfer);
                        }
                        case "5" -> details(ledger.getWallet(walletId(scanner, ledger)), ledger);
                        case "6" -> {
                            UUID wallet = walletId(scanner, ledger);
                            YearMonth month = YearMonth.parse(prompt(scanner, "Month (YYYY-MM): "));
                            ledger.setBudget(wallet, month, amount(scanner));
                            System.out.println("Budget saved. Limits track spending and do not block expenses.");
                        }
                        case "7" -> {
                            UUID wallet = walletId(scanner, ledger);
                            String name = prompt(scanner, "Goal name: ");
                            ledger.setSavingsGoal(wallet, name, amount(scanner));
                            System.out.println("Savings goal saved.");
                        }
                        case "0" -> running = false;
                        default -> System.out.println("Choose a menu option from 0 to 7.");
                    }
                } catch (IllegalArgumentException | DateTimeParseException e) {
                    ConsoleUI.notice("Oops! " + e.getMessage());
                } catch (NoSuchElementException e) {
                    running = false; // EOF also saves, including redirected CLI sessions.
                }
            }
            storage.save(ledger);
            ConsoleUI.notice("GAME SAVED. Your coins are tucked away. See you, sprout!");
        } catch (IOException e) {
            System.err.println("Storage error: " + e.getMessage());
            System.err.println("Bursa stopped. Any existing data file was preserved.");
            System.exit(1);
        }
    }

    private static String prompt(Scanner scanner, String text) {
        System.out.print(text);
        return scanner.nextLine().trim();
    }

    private static BigDecimal amount(Scanner scanner) {
        return new BigDecimal(prompt(scanner, "Amount (PHP): "));
    }

    private static UUID walletId(Scanner scanner, LedgerService ledger) {
        if (ledger.getWallets().isEmpty()) throw new IllegalArgumentException("Create a pouch with option 2 first.");
        listWallets(ledger);
        String selected = prompt(scanner, "  Choose pouch number > ");
        if (selected.matches("[0-9]+")) {
            int index = Integer.parseInt(selected) - 1;
            if (index < 0 || index >= ledger.getWallets().size()) throw new IllegalArgumentException("Choose a listed pouch number.");
            return ledger.getWallets().get(index).getId();
        }
        return UUID.fromString(selected);
    }

    private static void listWallets(LedgerService ledger) {
        ConsoleUI.title("YOUR SATCHEL / WALLETS");
        if (ledger.getWallets().isEmpty()) ConsoleUI.line("An empty satchel! Choose [2] to create your first pouch.");
        int number = 1;
        for (Wallet wallet : ledger.getWallets()) {
            ConsoleUI.line("[" + number++ + "] " + wallet.getName() + " / " + wallet.getType());
            ConsoleUI.line("    PHP " + wallet.getBalance());
        }
        ConsoleUI.border();
    }

    private static void details(Wallet wallet, LedgerService ledger) {
        ConsoleUI.title("POUCH INSPECTION / " + wallet.getName());
        System.out.println(wallet.getName() + " (" + wallet.getType() + ") | PHP " + wallet.getBalance());
        wallet.getBudgets().entrySet().stream().sorted(java.util.Map.Entry.comparingByKey()).forEach(entry -> {
            MonthlyBudget budget = entry.getValue();
            BigDecimal spent = wallet.expensesFor(budget.getMonth(), ledger.getZone());
            System.out.println("Budget " + budget.getMonth() + ": spent " + spent + " / "
                    + budget.getLimit() + ", remaining " + budget.remaining(spent));
        });
        wallet.getSavingsGoal().ifPresent(goal -> System.out.println("Quest: " + goal.getName()
                + ": " + wallet.getBalance() + " / " + goal.getTarget()
                + " (" + goal.progressPercent(wallet.getBalance()) + "%)"
                + (goal.isReached(wallet.getBalance()) ? " reached" : "")));
        wallet.getSavingsGoal().ifPresent(goal -> ConsoleUI.notice(ConsoleUI.bar(goal.progressPercent(wallet.getBalance()))));
        ConsoleUI.notice("COIN JOURNAL");
        if (wallet.getHistory().isEmpty()) ConsoleUI.notice("No entries yet. Your story starts with a coin.");
        wallet.getHistory().forEach(System.out::println);
    }
}
