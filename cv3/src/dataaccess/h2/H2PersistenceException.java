package dataaccess.h2;

import java.sql.SQLException;

/**
 * Unchecked wrapper for {@link SQLException}, so JDBC types do not leak into the business layer.
 * Keeps the SQLState so callers and tests can recognise integrity violations (SQLState class 23).
 */
public class H2PersistenceException extends RuntimeException {
    private final String sqlState;

    public H2PersistenceException(String operation, SQLException cause) {
        super(operation + " selhalo: " + cause.getMessage(), cause);
        this.sqlState = cause.getSQLState();
    }

    public String getSqlState() {
        return sqlState;
    }

    /** True for PK, FK, NOT NULL and CHECK violations. */
    public boolean isConstraintViolation() {
        return sqlState != null && sqlState.startsWith("23");
    }
}
