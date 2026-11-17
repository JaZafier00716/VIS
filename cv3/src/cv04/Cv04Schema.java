package cv04;

import dataaccess.h2.H2Database;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

/** The CV04 comparison's own tables in their own H2 file, independent of the application schema. */
public final class Cv04Schema {
    public static final String STATUS_WAITING = "CEKA_NA_POTVRZENI";

    private Cv04Schema() {}

    /** {@code <vis.data.dir>/cv04-h2} (default {@code data/cv04-h2}). */
    public static H2Database database() {
        return database(Path.of(System.getProperty("vis.data.dir", "data")));
    }

    public static H2Database database(Path dataDirectory) {
        return new H2Database(dataDirectory.resolve("cv04-h2"), Cv04Schema::create);
    }

    public static void create(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS cv04_customers (
                        customer_id VARCHAR(64)  PRIMARY KEY,
                        name        VARCHAR(200) NOT NULL,
                        email       VARCHAR(200) NOT NULL
                    )""");
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS cv04_materials (
                        material_id VARCHAR(64)  PRIMARY KEY,
                        name        VARCHAR(200) NOT NULL
                    )""");
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS cv04_orders (
                        order_id     VARCHAR(64)  PRIMARY KEY,
                        customer_id  VARCHAR(64)  NOT NULL REFERENCES cv04_customers (customer_id),
                        material_id  VARCHAR(64)  NOT NULL REFERENCES cv04_materials (material_id),
                        width        DOUBLE       NOT NULL CHECK (width > 0),
                        height       DOUBLE       NOT NULL CHECK (height > 0),
                        depth        DOUBLE       NOT NULL CHECK (depth > 0),
                        design_notes VARCHAR(1000),
                        status       VARCHAR(32)  NOT NULL,
                        created_at   TIMESTAMP(9) WITH TIME ZONE NOT NULL
                    )""");
        }
    }
}
