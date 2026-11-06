import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.ObjectInputFilter;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.ZoneId;
import java.util.Set;

/** Local, versioned Java serialization. Keep the lock for the entire CLI session. */
public final class StorageManager implements LedgerStorage {
    private static final String HEADER = "BURSA-1";
    private static final long MAX_BYTES = 64L * 1024 * 1024;
    private static final Set<String> ALLOWED = Set.of(
            "LedgerService", "Wallet", "WalletType",
            "Transaction", "TransactionType", "MonthlyBudget", "SavingsGoal",
            "java.util.LinkedHashMap", "java.util.HashMap", "java.util.ArrayList",
            "java.util.Map$Entry", "java.util.UUID", "java.lang.Object", "java.lang.String",
            "java.lang.Enum", "java.lang.Number", "java.math.BigDecimal", "java.math.BigInteger",
            "java.time.Ser", "java.time.Instant", "java.time.YearMonth",
            "java.time.ZoneId", "java.time.ZoneRegion", "java.time.ZoneOffset");
    private final Path file;
    private final FileChannel lockChannel;
    private final FileLock lock;

    public StorageManager(Path file) throws IOException {
        this.file = file.toAbsolutePath().normalize();
        Files.createDirectories(this.file.getParent());
        lockChannel = FileChannel.open(this.file.resolveSibling(this.file.getFileName() + ".lock"),
                StandardOpenOption.CREATE, StandardOpenOption.WRITE);
        FileLock acquired;
        try {
            acquired = lockChannel.tryLock();
        } catch (OverlappingFileLockException | IOException e) {
            lockChannel.close();
            throw new IOException("Cannot lock data file; another Bursa session may be open.", e);
        }
        if (acquired == null) {
            lockChannel.close();
            throw new IOException("Another Bursa session is using this data file.");
        }
        lock = acquired;
    }

    public synchronized LedgerService load() throws IOException {
        requireOpen();
        if (!Files.exists(file)) return new LedgerService(ZoneId.of("Asia/Manila"));
        if (Files.size(file) > MAX_BYTES) throw new IOException("Data file exceeds the 64 MiB limit.");
        try (ObjectInputStream input = new ObjectInputStream(
                new BufferedInputStream(Files.newInputStream(file)))) {
            input.setObjectInputFilter(info -> {
                if (info.depth() > 50 || info.references() > 1_000_000
                        || info.streamBytes() > MAX_BYTES || info.arrayLength() > 1_000_000) {
                    return ObjectInputFilter.Status.REJECTED;
                }
                Class<?> type = info.serialClass();
                if (type == null) return ObjectInputFilter.Status.UNDECIDED;
                while (type.isArray()) type = type.getComponentType();
                return type.isPrimitive() || ALLOWED.contains(type.getName())
                        ? ObjectInputFilter.Status.ALLOWED : ObjectInputFilter.Status.REJECTED;
            });
            if (!HEADER.equals(input.readUTF())) throw new IOException("Unsupported Bursa data version.");
            Object result = input.readObject();
            if (!(result instanceof LedgerService ledger)) throw new IOException("Invalid Bursa data file.");
            return ledger;
        } catch (ClassNotFoundException | RuntimeException e) {
            throw new IOException("Cannot load Bursa data. The original file was preserved.", e);
        }
    }

    /** Flush to disk, then atomically replace. Failure leaves the previous snapshot intact. */
    public synchronized void save(LedgerService ledger) throws IOException {
        requireOpen();
        Path temporary = Files.createTempFile(file.getParent(), "bursa-", ".tmp");
        try {
            synchronized (ledger) {
                try (FileChannel channel = FileChannel.open(temporary, StandardOpenOption.WRITE);
                     ObjectOutputStream output = new ObjectOutputStream(new BufferedOutputStream(
                             java.nio.channels.Channels.newOutputStream(channel)))) {
                    output.writeUTF(HEADER);
                    output.writeObject(ledger);
                    output.flush();
                    channel.force(true);
                }
            }
            if (Files.size(temporary) > MAX_BYTES) throw new IOException("Data exceeds the 64 MiB limit.");
            // Do not silently fall back to a non-atomic overwrite on unsupported file systems.
            Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private void requireOpen() throws IOException {
        if (!lock.isValid()) throw new IOException("Storage manager is closed.");
    }

    @Override public synchronized void close() throws IOException {
        try { lock.release(); } finally { lockChannel.close(); }
    }
}
