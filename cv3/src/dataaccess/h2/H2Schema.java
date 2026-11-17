package dataaccess.h2;

import dataaccess.SchemaVersion;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * The complete H2 schema, created on first connection. Each CV06 pattern maps onto a real database feature.
 * The customer tables depend on the CV07 inheritance strategy: {@link #cti()} or {@link #sti()}.
 */
public final class H2Schema {
    private H2Schema() {}

    /** CTI (cv07 p.7–8): the common table plus one table per subtype; their ID is PK and FK to customers. */
    static final String[] CTI_CUSTOMERS = {
            """
            CREATE TABLE IF NOT EXISTS customers (
                customer_id      VARCHAR(64)  PRIMARY KEY,
                customer_type    CHAR(1)      NOT NULL,
                email            VARCHAR(200) NOT NULL,
                phone            VARCHAR(30),
                billing_street   VARCHAR(200),
                billing_city     VARCHAR(100),
                billing_zip_code VARCHAR(20),
                billing_country  VARCHAR(100),
                CONSTRAINT ck_customers_type CHECK (customer_type IN ('P', 'C')),
                -- Embedded Value BillingAddress is optional: all four columns or none
                CONSTRAINT ck_customers_billing CHECK (
                    (billing_street IS NULL AND billing_city IS NULL AND billing_zip_code IS NULL
                        AND billing_country IS NULL)
                 OR (billing_street IS NOT NULL AND billing_city IS NOT NULL AND billing_zip_code IS NOT NULL
                        AND billing_country IS NOT NULL))
            )""",
            """
            CREATE TABLE IF NOT EXISTS person_customers (
                customer_id VARCHAR(64)  PRIMARY KEY,
                first_name  VARCHAR(100) NOT NULL,
                last_name   VARCHAR(100) NOT NULL,
                birth_date  DATE         NOT NULL,
                CONSTRAINT fk_person_customers_customer FOREIGN KEY (customer_id)
                    REFERENCES customers(customer_id) ON DELETE CASCADE
            )""",
            """
            CREATE TABLE IF NOT EXISTS company_customers (
                customer_id    VARCHAR(64)  PRIMARY KEY,
                company_name   VARCHAR(200) NOT NULL,
                ico            CHAR(8)      NOT NULL,
                dic            VARCHAR(12),
                contact_person VARCHAR(200),
                CONSTRAINT fk_company_customers_customer FOREIGN KEY (customer_id)
                    REFERENCES customers(customer_id) ON DELETE CASCADE
            )"""
    };

    /** STI (cv07 p.5–6): one table for the whole hierarchy, discriminator customer_type P/C. */
    static final String[] STI_CUSTOMERS = {
            """
            CREATE TABLE IF NOT EXISTS customers_sti (
                customer_id      VARCHAR(64)  PRIMARY KEY,
                customer_type    CHAR(1)      NOT NULL,
                email            VARCHAR(200) NOT NULL,
                phone            VARCHAR(30),
                billing_street   VARCHAR(200),
                billing_city     VARCHAR(100),
                billing_zip_code VARCHAR(20),
                billing_country  VARCHAR(100),
                first_name       VARCHAR(100),
                last_name        VARCHAR(100),
                birth_date       DATE,
                company_name     VARCHAR(200),
                ico              CHAR(8),
                dic              VARCHAR(12),
                contact_person   VARCHAR(200),
                CONSTRAINT ck_customers_sti_billing CHECK (
                    (billing_street IS NULL AND billing_city IS NULL AND billing_zip_code IS NULL
                        AND billing_country IS NULL)
                 OR (billing_street IS NOT NULL AND billing_city IS NOT NULL AND billing_zip_code IS NOT NULL
                        AND billing_country IS NOT NULL)),
                -- the right columns are filled for each type, the other type's columns are NULL
                CONSTRAINT ck_customers_sti_type CHECK (
                    (customer_type = 'P'
                        AND first_name IS NOT NULL AND last_name IS NOT NULL AND birth_date IS NOT NULL
                        AND company_name IS NULL AND ico IS NULL AND dic IS NULL AND contact_person IS NULL)
                 OR (customer_type = 'C'
                        AND company_name IS NOT NULL AND ico IS NOT NULL
                        AND first_name IS NULL AND last_name IS NULL AND birth_date IS NULL))
            )"""
    };

    /** Tables that do not depend on the customer hierarchy. */
    static final String[] CATALOG = {
            """
            CREATE TABLE IF NOT EXISTS materials (
                material_id VARCHAR(64) PRIMARY KEY,
                name        VARCHAR(200) NOT NULL
            )""",
            """
            CREATE TABLE IF NOT EXISTS finishes (
                finish_id VARCHAR(64) PRIMARY KEY,
                name      VARCHAR(200) NOT NULL
            )""",
            // Association Table Mapping: composite PK prevents duplicate pairs, two FKs.
            """
            CREATE TABLE IF NOT EXISTS material_finishes (
                material_id VARCHAR(64) NOT NULL,
                finish_id   VARCHAR(64) NOT NULL,
                CONSTRAINT pk_material_finishes PRIMARY KEY (material_id, finish_id),
                CONSTRAINT fk_material_finishes_material FOREIGN KEY (material_id)
                    REFERENCES materials(material_id) ON DELETE CASCADE,
                CONSTRAINT fk_material_finishes_finish FOREIGN KEY (finish_id) REFERENCES finishes(finish_id)
            )"""
    };

    /** Tables that reference the customer; {@code %1$s} is the hierarchy's root table of the active strategy. */
    static final String[] CUSTOMER_REFERENCES = {
            // Addresses belong to their customer: deleting the customer removes them.
            """
            CREATE TABLE IF NOT EXISTS addresses (
                address_id  VARCHAR(64) PRIMARY KEY,
                customer_id VARCHAR(64) NOT NULL,
                street      VARCHAR(200),
                city        VARCHAR(100),
                zip_code    VARCHAR(20),
                country     VARCHAR(100),
                seq         BIGINT GENERATED BY DEFAULT AS IDENTITY,
                CONSTRAINT fk_addresses_customer FOREIGN KEY (customer_id)
                    REFERENCES %1$s(customer_id) ON DELETE CASCADE
            )""",
            // Foreign Key Mapping (customer_id, material_id) + Embedded Value (width, height, depth, design_notes).
            """
            CREATE TABLE IF NOT EXISTS orders (
                order_id     VARCHAR(64) PRIMARY KEY,
                customer_id  VARCHAR(64) NOT NULL,
                material_id  VARCHAR(64) NOT NULL,
                width        DOUBLE PRECISION NOT NULL,
                height       DOUBLE PRECISION NOT NULL,
                depth        DOUBLE PRECISION NOT NULL,
                design_notes VARCHAR(1000),
                status       VARCHAR(40) NOT NULL,
                created_at   TIMESTAMP(9) WITH TIME ZONE NOT NULL,
                CONSTRAINT fk_orders_customer FOREIGN KEY (customer_id) REFERENCES %1$s(customer_id),
                CONSTRAINT fk_orders_material FOREIGN KEY (material_id) REFERENCES materials(material_id)
            )""",
            // Dependent Mapping: items live and die with their order. Serialized LOB: configuration_json.
            """
            CREATE TABLE IF NOT EXISTS order_items (
                order_id           VARCHAR(64) NOT NULL,
                line_number        INT NOT NULL,
                material_id        VARCHAR(64) NOT NULL,
                quantity           INT NOT NULL,
                unit_price         NUMERIC(12, 2) NOT NULL,
                configuration_json CLOB,
                CONSTRAINT pk_order_items PRIMARY KEY (order_id, line_number),
                CONSTRAINT fk_order_items_order FOREIGN KEY (order_id) REFERENCES orders(order_id) ON DELETE CASCADE,
                CONSTRAINT fk_order_items_material FOREIGN KEY (material_id) REFERENCES materials(material_id),
                CONSTRAINT ck_order_items_quantity CHECK (quantity > 0),
                CONSTRAINT ck_order_items_price CHECK (unit_price >= 0)
            )"""
    };

    /** The schema with Class Table Inheritance for customers (the default). */
    public static H2Database.Schema cti() {
        return connection -> create(connection, CTI_CUSTOMERS, "customers");
    }

    /** The schema with Single Table Inheritance for customers. */
    public static H2Database.Schema sti() {
        return connection -> create(connection, STI_CUSTOMERS, "customers_sti");
    }

    static void create(Connection connection) throws SQLException {
        create(connection, CTI_CUSTOMERS, "customers");
    }

    /**
     * Creates the tables of one inheritance strategy: its customer tables, the catalog, and the tables that
     * reference the customer (FKs to {@code rootTable}). Each strategy has its own database file, because
     * {@code orders} and {@code addresses} can reference only one customer table.
     *
     * Before that it checks that the file is not from an older layout: an old file has tables but no
     * {@code schema_version} (or another version), and CREATE TABLE IF NOT EXISTS would not change them.
     */
    private static void create(Connection connection, String[] customerTables, String rootTable) throws SQLException {
        boolean versioned = tableExists(connection, "SCHEMA_VERSION");
        if (versioned) {
            int stored = storedVersion(connection);
            if (stored != SchemaVersion.CURRENT) {
                throw SchemaVersion.outdated("H2 verze " + stored + ", očekávána " + SchemaVersion.CURRENT);
            }
        } else if (tableExists(connection, "CUSTOMERS") || tableExists(connection, "CUSTOMERS_STI")
                || tableExists(connection, "ORDERS")) {
            throw SchemaVersion.outdated("H2 bez schema_version");
        }
        try (Statement statement = connection.createStatement()) {
            for (String ddl : customerTables) {
                statement.execute(ddl);
            }
            for (String ddl : CATALOG) {
                statement.execute(ddl);
            }
            for (String ddl : CUSTOMER_REFERENCES) {
                statement.execute(ddl.formatted(rootTable));
            }
            if (!versioned) {
                statement.execute("CREATE TABLE schema_version (version INT NOT NULL)");
                statement.execute("INSERT INTO schema_version (version) VALUES (" + SchemaVersion.CURRENT + ")");
            }
        }
    }

    private static boolean tableExists(Connection connection, String table) throws SQLException {
        try (ResultSet rs = connection.getMetaData().getTables(null, "PUBLIC", table, new String[]{"TABLE"})) {
            return rs.next();
        }
    }

    private static int storedVersion(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery("SELECT MAX(version) FROM schema_version")) {
            return rs.next() ? rs.getInt(1) : 0;
        }
    }
}
