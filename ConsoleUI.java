import java.math.BigDecimal;

/** Original vegetable-adventure console artwork and accessible text navigation. */
public final class ConsoleUI {
    private static final int WIDTH = 62;
    private static final boolean COLOR = System.console() != null
            && System.getenv("NO_COLOR") == null && !"dumb".equals(System.getenv("TERM"));
    private ConsoleUI() { }

    private static String tint(String text, String code) {
        return COLOR ? "\033[" + code + "m" + text + "\033[0m" : text;
    }

    public static void border() {
        System.out.println(tint("+" + "-".repeat(WIDTH + 2) + "+", "38;5;108"));
    }

    public static void line(String text) {
        // Strip control characters from user-provided names before rendering.
        String clean = text.replaceAll("\\p{Cntrl}", "?");
        do {
            String part = clean.substring(0, Math.min(WIDTH, clean.length()));
            System.out.println(tint("| ", "38;5;108") + tint(String.format("%-" + WIDTH + "s", part), "38;5;230")
                    + tint(" |", "38;5;108"));
            clean = clean.substring(part.length());
        } while (!clean.isEmpty());
    }

    public static void title(String title) {
        System.out.println();
        border();
        line(title);
        border();
    }

    public static void welcome() {
        title("B U R S A  /  THE LITTLE LEDGER ADVENTURE");
        line("       \\ | /       A tiny turnip. A tidy treasury.");
        line("        \\|/        ___________________________");
        line("      .----- .     | Save coins. Grow dreams. |");
        line("     /  o  o  \\    |_________________________|");
        line("     \\   v   /     Your offline money village");
        line("      '.___.'      PHP / local save / no internet");
        line("         v");
        border();
    }

    public static void menu(LedgerService ledger) {
        BigDecimal total = ledger.getWallets().stream().map(Wallet::getBalance)
                .reduce(new BigDecimal("0.00"), BigDecimal::add);
        title("VILLAGE SQUARE                         [ YOUR ADVENTURE ]");
        line("Treasury: PHP " + total + "    Wallets: " + ledger.getWallets().size());
        line("");
        line("[1] Satchel          View your wallets");
        line("[2] New pouch        Create a wallet");
        line("[3] Coin journal     Add income or expense");
        line("[4] Courier          Transfer between wallets");
        line("[5] Inspect pouch    Balance, budgets & history");
        line("[6] Monthly rations  Set a spending budget");
        line("[7] Quest board      Set a savings goal");
        line("");
        line("[0] Save & rest      Save your progress and exit");
        border();
        System.out.println(tint("  Sprout says: Every little coin has a place.", "38;5;180"));
    }

    public static String bar(BigDecimal percentage) {
        int filled = percentage.max(BigDecimal.ZERO).min(new BigDecimal("100")).intValue() / 5;
        return "[" + "#".repeat(filled) + ".".repeat(20 - filled) + "] " + percentage + "%";
    }

    public static void notice(String text) {
        System.out.println(tint("  * " + text, "38;5;180"));
    }
}
