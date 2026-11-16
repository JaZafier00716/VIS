package cz.vsb.catering.data.oracle;

import cz.vsb.catering.business.port.TransactionRunner;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.function.Supplier;

/** Singleton (GoF): the one connection provider shared by all Oracle mappers.
 *  Also the TransactionRunner: one JDBC connection per business operation, bound to the thread,
 *  commit at the end, rollback on any exception. Nested calls join the running transaction. */
public final class Database implements TransactionRunner {
    private static Database instance;

    private final String url;
    private final String user;
    private final String password;
    private final ThreadLocal<Connection> current = new ThreadLocal<>();

    private Database(String url, String user, String password) {
        this.url = url;
        this.user = user;
        this.password = password;
    }

    /** Called once at start-up with the values from config/db.properties. */
    public static synchronized Database init(String url, String user, String password) {
        if (instance == null) {
            DriverManager.setLoginTimeout(5);                        // seconds; an unreachable DB must not hang the UI
            instance = new Database(url, user, password);
        }
        return instance;
    }

    public static synchronized Database getInstance() {
        if (instance == null) throw new IllegalStateException("Databáze nebyla inicializována (chybí Database.init()).");
        return instance;
    }

    @Override
    public <T> T inTransaction(Supplier<T> work) {
        if (current.get() != null) return work.get();                // join the outer transaction
        try (Connection connection = DriverManager.getConnection(url, user, password)) {
            connection.setAutoCommit(false);
            current.set(connection);
            try {
                T result = work.get();
                connection.commit();
                return result;
            } catch (RuntimeException | Error e) {
                try {
                    connection.rollback();
                } catch (SQLException rollbackError) {
                    e.addSuppressed(rollbackError);                  // keep the original failure
                }
                throw e;
            } finally {
                current.remove();
            }
        } catch (SQLException e) {
            throw new DataAccessException("Chyba připojení k databázi: " + e.getMessage(), e);
        }
    }

    /** Connection of the running transaction; mappers must be called inside inTransaction(). */
    public Connection connection() {
        Connection connection = current.get();
        if (connection == null) throw new IllegalStateException("Mapper volán mimo transakci (chybí inTransaction).");
        return connection;
    }
}
