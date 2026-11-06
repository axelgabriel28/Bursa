import java.io.IOException;

/** Persistence boundary, allowing a different local storage format later. */
public interface LedgerStorage extends AutoCloseable {
    LedgerService load() throws IOException;
    void save(LedgerService ledger) throws IOException;
    @Override void close() throws IOException;
}
