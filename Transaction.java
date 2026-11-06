import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Immutable entry. Amounts are positive; type determines the balance direction. */
public final class Transaction implements Serializable {
    private static final long serialVersionUID = 1L;
    private final UUID id;
    private final Instant timestamp;
    private final TransactionType type;
    private final BigDecimal amount;
    private final String category;
    private final UUID transferId;
    private final UUID counterpartyWalletId;

    Transaction(Instant timestamp, TransactionType type, BigDecimal amount,
                String category, UUID transferId, UUID counterpartyWalletId) {
        this.id = UUID.randomUUID();
        this.timestamp = Objects.requireNonNull(timestamp);
        this.type = Objects.requireNonNull(type);
        this.amount = Money.positive(amount);
        if (category == null || category.isBlank()) {
            throw new IllegalArgumentException("Category is required.");
        }
        this.category = category.trim();
        boolean transfer = type == TransactionType.TRANSFER_IN || type == TransactionType.TRANSFER_OUT;
        if (transfer != (transferId != null && counterpartyWalletId != null)
                || (!transfer && (transferId != null || counterpartyWalletId != null))) {
            throw new IllegalArgumentException("Invalid transfer references.");
        }
        this.transferId = transferId;
        this.counterpartyWalletId = counterpartyWalletId;
    }

    public UUID getId() { return id; }
    public Instant getTimestamp() { return timestamp; }
    public TransactionType getType() { return type; }
    public BigDecimal getAmount() { return amount; }
    public String getCategory() { return category; }
    public UUID getTransferId() { return transferId; }
    public UUID getCounterpartyWalletId() { return counterpartyWalletId; }

    BigDecimal signedAmount() {
        return type == TransactionType.EXPENSE || type == TransactionType.TRANSFER_OUT
                ? amount.negate() : amount;
    }

    @Override public String toString() {
        return timestamp + " | " + type + " | " + amount + " | " + category
                + (transferId == null ? "" : " | transfer=" + transferId
                + " | wallet=" + counterpartyWalletId);
    }
}
