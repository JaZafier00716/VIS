package cz.vsb.catering.data.oracle;

import cz.vsb.catering.business.domain.ConflictException;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Small JDBC helpers shared by the Oracle mappers: binding parameters, reading rows and
 *  wrapping SQLException. Times are converted in the JVM default zone (Timestamp). */
final class Jdbc {
    @FunctionalInterface
    interface RowMapper<T> { T map(ResultSet rs) throws SQLException; }

    private Jdbc() {}

    static <T> List<T> query(String sql, RowMapper<T> mapper, Object... params) {
        try (PreparedStatement ps = connection().prepareStatement(sql)) {
            bind(ps, params);
            try (ResultSet rs = ps.executeQuery()) {
                List<T> result = new ArrayList<>();
                while (rs.next()) result.add(mapper.map(rs));
                return result;
            }
        } catch (SQLException e) {
            throw new DataAccessException("Chyba při čtení z databáze: " + e.getMessage(), e);
        }
    }

    static <T> Optional<T> queryOne(String sql, RowMapper<T> mapper, Object... params) {
        return query(sql, mapper, params).stream().findFirst();
    }

    /** INSERT returning the generated surrogate key (Identity Field). */
    static long insert(String sql, String idColumn, Object... params) {
        try (PreparedStatement ps = connection().prepareStatement(sql, new String[]{idColumn})) {
            bind(ps, params);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                keys.next();
                return keys.getLong(1);
            }
        } catch (SQLException e) {
            throw writeFailure(e);
        }
    }

    /** INSERT / UPDATE / DELETE; returns the number of rows. */
    static int update(String sql, Object... params) {
        try (PreparedStatement ps = connection().prepareStatement(sql)) {
            bind(ps, params);
            return ps.executeUpdate();
        } catch (SQLException e) {
            throw writeFailure(e);
        }
    }

    /** ORA-00001 (unique / PK violation) means another worker was first: a conflict, not a technical error. */
    private static RuntimeException writeFailure(SQLException e) {
        if (e.getErrorCode() == 1)
            return new ConflictException("Záznam se stejnou hodnotou už existuje nebo jej mezitím změnil jiný pracovník. Načtěte data znovu.");
        return new DataAccessException("Chyba při zápisu do databáze: " + e.getMessage(), e);
    }

    static LocalDateTime dateTime(ResultSet rs, String column) throws SQLException {
        Timestamp ts = rs.getTimestamp(column);
        return ts == null ? null : ts.toLocalDateTime();
    }

    static Long longOrNull(ResultSet rs, String column) throws SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }

    static String yesNo(boolean value) { return value ? "Y" : "N"; }

    private static Connection connection() { return Database.getInstance().connection(); }

    private static void bind(PreparedStatement ps, Object... params) throws SQLException {
        for (int i = 0; i < params.length; i++) {
            Object p = params[i];
            if (p == null) ps.setNull(i + 1, Types.VARCHAR);       // Oracle accepts a NULL of any type here
            else if (p instanceof LocalDateTime t) ps.setTimestamp(i + 1, Timestamp.valueOf(t));
            else if (p instanceof LocalDate d) ps.setDate(i + 1, Date.valueOf(d));
            else ps.setObject(i + 1, p);
        }
    }
}
