package dataaccess.h2;

import dataaccess.dto.MaterialDto;
import dataaccess.dto.OrderDto;
import dataaccess.dto.OrderItemDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import support.TestCustomers;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The H2 schema enforces the CV06 patterns at database level, accessed through plain JDBC. */
class H2DatabaseTest {
    @TempDir
    Path dir;
    private H2Database database;

    @BeforeEach
    void setUp() {
        database = new H2Database(dir.resolve("vis-h2"));
        H2Storage.gateways(database).customers().insert(TestCustomers.person("C-1", "Jana", "jana@example.com"));
        new H2MaterialGateway(database).insert(new MaterialDto("M-1", "Dub"));
    }

    @Test
    void schemaHasAllTables() {
        Set<String> tables = database.run("tabulky", connection -> {
            DatabaseMetaData meta = connection.getMetaData();
            java.util.Set<String> names = new java.util.TreeSet<>();
            try (ResultSet rs = meta.getTables(null, "PUBLIC", "%", new String[]{"TABLE"})) {
                while (rs.next()) names.add(rs.getString("TABLE_NAME").toLowerCase());
            }
            return names;
        });
        assertEquals(Set.of("customers", "addresses", "materials", "finishes", "material_finishes", "orders",
                "order_items", "schema_version", "person_customers", "company_customers"), tables);
    }

    @Test
    void foreignKeyRejectsOrderForMissingCustomer() {
        H2PersistenceException e = assertThrows(H2PersistenceException.class,
                () -> new H2OrderGateway(database).insert(order("O-1", "C-404", List.of())));
        assertTrue(e.isConstraintViolation());
    }

    @Test
    void foreignKeyRejectsOrderForMissingMaterial() {
        OrderDto order = order("O-1", "C-1", List.of());
        OrderDto withMissingMaterial = new OrderDto(order.orderId(), order.customerId(), "M-404", order.width(),
                order.height(), order.depth(), order.designNotes(), order.status(), order.createdAt(), List.of());

        H2PersistenceException e = assertThrows(H2PersistenceException.class,
                () -> new H2OrderGateway(database).insert(withMissingMaterial));
        assertTrue(e.isConstraintViolation());
        assertEquals(0, H2Records.count(database, "orders"));
    }

    @Test
    void foreignKeyRejectsOrderItemForMissingMaterial() {
        H2PersistenceException e = assertThrows(H2PersistenceException.class, () -> new H2OrderGateway(database)
                .insert(order("O-1", "C-1", List.of(new OrderItemDto(1, "M-404", 1, BigDecimal.ONE, null)))));
        assertTrue(e.isConstraintViolation());
        assertEquals(0, H2Records.count(database, "orders"), "order and items are written atomically");
        assertEquals(0, H2Records.count(database, "order_items"));
    }

    @Test
    void orderAndItemsRoundTripIncludingTheClob() {
        H2OrderGateway orders = new H2OrderGateway(database);
        String json = "{\"version\":1,\"color\":\"bílá\",\"machining\":\"CNC\",\"options\":{}}";
        OrderDto order = order("O-1", "C-1", List.of(
                new OrderItemDto(1, "M-1", 2, new BigDecimal("10.50"), json),
                new OrderItemDto(2, "M-1", 1, new BigDecimal("3.00"), null)));
        orders.insert(order);

        OrderDto loaded = orders.findById("O-1").orElseThrow();

        assertEquals(order, loaded);
        assertEquals(json, loaded.items().get(0).configurationJson());
    }

    @Test
    void deletingAnOrderCascadesToItsItems() {
        H2OrderGateway orders = new H2OrderGateway(database);
        orders.insert(order("O-1", "C-1", List.of(new OrderItemDto(1, "M-1", 1, BigDecimal.ONE, null))));

        orders.delete("O-1");

        assertEquals(0, H2Records.count(database, "order_items"));
    }

    @Test
    void checkConstraintRejectsNonPositiveQuantity() {
        assertThrows(H2PersistenceException.class, () -> new H2OrderGateway(database)
                .insert(order("O-1", "C-1", List.of(new OrderItemDto(1, "M-1", 0, BigDecimal.ONE, null)))));
        assertEquals(0, H2Records.count(database, "orders"), "order and items are written atomically");
    }

    @Test
    void transactionRollbackUndoesAllStatements() {
        H2Transaction transaction = new H2Transaction(database);
        transaction.begin();
        H2Storage.gateways(database).customers().insert(TestCustomers.person("C-2", "Petr", "petr@example.com"));
        new H2MaterialGateway(database).insert(new MaterialDto("M-2", "Buk"));
        transaction.rollback();

        assertEquals(1, H2Records.count(database, "customers"));
        assertEquals(1, H2Records.count(database, "materials"));
    }

    private static OrderDto order(String id, String customerId, List<OrderItemDto> items) {
        return new OrderDto(id, customerId, "M-1", 100, 80, 50, "pozn.", "CEKA_NA_POTVRZENI",
                Instant.now().truncatedTo(ChronoUnit.MICROS), items);
    }
}
