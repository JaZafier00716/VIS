package dataaccess.h2;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/**
 * Hand-written connection and transaction handling for the file-based H2 database.
 *
 * Outside a transaction every operation opens its own connection (try-with-resources). While a Unit of Work
 * transaction is open ({@link #begin()}), all operations share its single connection, so the whole commit is
 * committed or rolled back together.
 */
public class H2Database {
    @FunctionalInterface
    public interface Work<T> {
        T run(Connection connection) throws SQLException;
    }

    /** Creates the tables on the first connection. */
    @FunctionalInterface
    public interface Schema {
        void create(Connection connection) throws SQLException;
    }

    private final String url;
    private final Schema schema;
    private boolean schemaCreated;
    private Connection transactionConnection;

    public H2Database(Path databaseFile) {
        this(databaseFile, H2Schema::create);
    }

    /** A database with another schema, e.g. the isolated CV04 comparison ({@code cv04.Cv04Schema}). */
    public H2Database(Path databaseFile, Schema schema) {
        this.url = "jdbc:h2:file:" + databaseFile.toAbsolutePath();
        this.schema = schema;
    }

    /** {@code <vis.data.dir>/vis-h2}, next to the JSON files of the same data directory. */
    public static H2Database inDataDirectory() {
        return new H2Database(Path.of(System.getProperty("vis.data.dir", "data")).resolve("vis-h2"));
    }

    /** Runs a read or a single-statement write. */
    public synchronized <T> T run(String operation, Work<T> work) {
        if (transactionConnection != null) {
            return wrap(operation, () -> work.run(transactionConnection));
        }
        try (Connection connection = open()) {
            return work.run(connection);
        } catch (SQLException e) {
            throw new H2PersistenceException(operation, e);
        }
    }

    /** Runs a write to several tables atomically; joins the open Unit of Work transaction if there is one. */
    public synchronized <T> T inTransaction(String operation, Work<T> work) {
        if (transactionConnection != null) {
            return wrap(operation, () -> work.run(transactionConnection));
        }
        try (Connection connection = open()) {
            connection.setAutoCommit(false);
            try {
                T result = work.run(connection);
                connection.commit();
                return result;
            } catch (SQLException | RuntimeException e) {
                connection.rollback();
                throw e;
            }
        } catch (SQLException e) {
            throw new H2PersistenceException(operation, e);
        }
    }

    synchronized void begin() {
        if (transactionConnection != null) {
            throw new IllegalStateException("H2 transakce je už otevřená.");
        }
        try {
            transactionConnection = open();
            transactionConnection.setAutoCommit(false);
        } catch (SQLException e) {
            closeTransactionConnection();
            throw new H2PersistenceException("Zahájení transakce", e);
        }
    }

    synchronized void commit() {
        try {
            transactionConnection.commit();
        } catch (SQLException e) {
            throw new H2PersistenceException("Potvrzení transakce (commit)", e);
        }
        closeTransactionConnection();
    }

    synchronized void rollback() {
        if (transactionConnection == null) {
            return;
        }
        try {
            transactionConnection.rollback();
        } catch (SQLException e) {
            throw new H2PersistenceException("Odvolání transakce (rollback)", e);
        } finally {
            closeTransactionConnection();
        }
    }

    private Connection open() throws SQLException {
        Connection connection = DriverManager.getConnection(url, "sa", "");
        if (!schemaCreated) {
            try {
                schema.create(connection);
            } catch (SQLException | RuntimeException e) {
                connection.close();
                throw e;
            }
            schemaCreated = true;
        }
        return connection;
    }

    private void closeTransactionConnection() {
        if (transactionConnection == null) {
            return;
        }
        try {
            transactionConnection.close();
        } catch (SQLException ignored) {
            // the transaction outcome is already decided; nothing more to undo
        } finally {
            transactionConnection = null;
        }
    }

    private interface SqlCall<T> {
        T call() throws SQLException;
    }

    private static <T> T wrap(String operation, SqlCall<T> call) {
        try {
            return call.call();
        } catch (SQLException e) {
            throw new H2PersistenceException(operation, e);
        }
    }
}
