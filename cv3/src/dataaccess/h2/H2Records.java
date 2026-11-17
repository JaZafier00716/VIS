package dataaccess.h2;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Set;

/** Small JDBC helpers shared by the H2 gateways. Table names are checked against a fixed list. */
public final class H2Records {
    private static final Set<String> TABLES =
            Set.of("customers", "addresses", "materials", "finishes", "material_finishes", "orders", "order_items");

    private H2Records() {}

    static int deleteById(Connection connection, String table, String idColumn, String id) throws SQLException {
        requireKnownTable(table);
        try (PreparedStatement statement = connection.prepareStatement(
                "DELETE FROM " + table + " WHERE " + idColumn + " = ?")) {
            statement.setString(1, id);
            return statement.executeUpdate();
        }
    }

    /** Number of rows in a table; used by tests and demonstrations to show what is really stored. */
    public static long count(H2Database database, String table) {
        requireKnownTable(table);
        return database.run("Počet záznamů v " + table, connection -> {
            try (PreparedStatement statement = connection.prepareStatement("SELECT COUNT(*) FROM " + table);
                 ResultSet rs = statement.executeQuery()) {
                rs.next();
                return rs.getLong(1);
            }
        });
    }

    private static void requireKnownTable(String table) {
        if (!TABLES.contains(table)) {
            throw new IllegalArgumentException("Neznámá tabulka " + table);
        }
    }
}
