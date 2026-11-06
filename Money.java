import java.math.BigDecimal;
import java.math.RoundingMode;

final class Money {
    private Money() { }

    static BigDecimal amount(BigDecimal value) {
        if (value == null) throw new IllegalArgumentException("Amount is required.");
        try {
            return value.setScale(2, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException e) {
            throw new IllegalArgumentException("Use at most two decimal places.");
        }
    }

    static BigDecimal positive(BigDecimal value) {
        BigDecimal result = amount(value);
        if (result.signum() <= 0) throw new IllegalArgumentException("Amount must be positive.");
        return result;
    }
}
