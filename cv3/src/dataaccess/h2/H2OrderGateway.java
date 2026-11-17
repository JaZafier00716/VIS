package dataaccess.h2;

import dataaccess.dto.OrderDto;
import dataaccess.dto.OrderItemDto;
import dataaccess.persistence.RecordIds;
import dataaccess.gateway.OrderGateway;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Table Data Gateway used by the Data Mapper (H2 tables orders + dependent order_items).
 * orders + dependent order_items, always written in one transaction. Referential integrity is enforced by the
 * database (FKs to customers and materials, ON DELETE CASCADE from orders to order_items).
 */
public class H2OrderGateway implements OrderGateway {
    private static final String ENTITY = "Objednávka";
    private static final String ORDER_COLUMNS =
            "order_id, customer_id, material_id, width, height, depth, design_notes, status, created_at";
    private final H2Database database;

    public H2OrderGateway(H2Database database) {
        this.database = database;
    }

    @Override
    public void insert(OrderDto order) {
        RecordIds.require(order.orderId(), ENTITY);
        database.inTransaction("Vložení objednávky " + order.orderId(), connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO orders (" + ORDER_COLUMNS + ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
                setOrderColumns(statement, order);
                statement.executeUpdate();
            }
            insertItems(connection, order);
            return null;
        });
    }

    @Override
    public void update(OrderDto order) {
        String id = RecordIds.require(order.orderId(), ENTITY);
        int updated = database.inTransaction("Úprava objednávky " + id, connection -> {
            int count;
            try (PreparedStatement statement = connection.prepareStatement(
                    "UPDATE orders SET customer_id = ?, material_id = ?, width = ?, height = ?, depth = ?, "
                            + "design_notes = ?, status = ?, created_at = ? WHERE order_id = ?")) {
                statement.setString(1, order.customerId());
                statement.setString(2, order.materialId());
                statement.setDouble(3, order.width());
                statement.setDouble(4, order.height());
                statement.setDouble(5, order.depth());
                statement.setString(6, order.designNotes());
                statement.setString(7, order.status());
                statement.setObject(8, OffsetDateTime.ofInstant(order.createdAt(), ZoneOffset.UTC));
                statement.setString(9, id);
                count = statement.executeUpdate();
            }
            if (count == 0) {
                return 0;
            }
            // Dependent Mapping: replace the item rows, which covers new, changed and removed items.
            try (PreparedStatement statement = connection.prepareStatement(
                    "DELETE FROM order_items WHERE order_id = ?")) {
                statement.setString(1, id);
                statement.executeUpdate();
            }
            insertItems(connection, order);
            return count;
        });
        if (updated == 0) {
            throw RecordIds.missing(ENTITY, id);
        }
    }

    @Override
    public void delete(String id) {
        RecordIds.require(id, ENTITY);
        int deleted = database.run("Smazání objednávky " + id, connection -> {
            try (PreparedStatement statement = connection.prepareStatement("DELETE FROM orders WHERE order_id = ?")) {
                statement.setString(1, id);
                return statement.executeUpdate();
            }
        });
        if (deleted == 0) {
            throw RecordIds.missing(ENTITY, id);
        }
    }

    @Override
    public Optional<OrderDto> findById(String id) {
        return database.run("Načtení objednávky " + id, connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT " + ORDER_COLUMNS + " FROM orders WHERE order_id = ?")) {
                statement.setString(1, id);
                try (ResultSet rs = statement.executeQuery()) {
                    if (!rs.next()) {
                        return Optional.empty();
                    }
                    return Optional.of(mapOrder(rs, items(connection, id)));
                }
            }
        });
    }

    @Override
    public List<OrderDto> findAll() {
        return database.run("Načtení objednávek", connection -> {
            Map<String, List<OrderItemDto>> itemsByOrder = new HashMap<>();
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT order_id, line_number, material_id, quantity, unit_price, configuration_json "
                            + "FROM order_items ORDER BY order_id, line_number");
                 ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    itemsByOrder.computeIfAbsent(rs.getString("order_id"), key -> new ArrayList<>()).add(mapItem(rs));
                }
            }
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT " + ORDER_COLUMNS + " FROM orders ORDER BY order_id");
                 ResultSet rs = statement.executeQuery()) {
                List<OrderDto> result = new ArrayList<>();
                while (rs.next()) {
                    result.add(mapOrder(rs, itemsByOrder.getOrDefault(rs.getString("order_id"), List.of())));
                }
                return result;
            }
        });
    }

    private static void setOrderColumns(PreparedStatement statement, OrderDto order) throws SQLException {
        statement.setString(1, order.orderId());
        statement.setString(2, order.customerId());
        statement.setString(3, order.materialId());
        // Embedded Value: OrderSpecification is stored as columns of the order row.
        statement.setDouble(4, order.width());
        statement.setDouble(5, order.height());
        statement.setDouble(6, order.depth());
        statement.setString(7, order.designNotes());
        statement.setString(8, order.status());
        statement.setObject(9, OffsetDateTime.ofInstant(order.createdAt(), ZoneOffset.UTC));
    }

    private static void insertItems(Connection connection, OrderDto order) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO order_items (order_id, line_number, material_id, quantity, unit_price, configuration_json) "
                        + "VALUES (?, ?, ?, ?, ?, ?)")) {
            for (OrderItemDto item : order.items()) {
                statement.setString(1, order.orderId());
                statement.setInt(2, item.lineNumber());
                statement.setString(3, item.materialId());
                statement.setInt(4, item.quantity());
                statement.setBigDecimal(5, item.unitPrice());
                // Serialized LOB: the configuration is already serialized to JSON text by OrderMapper.
                if (item.configurationJson() == null) {
                    statement.setNull(6, Types.CLOB);
                } else {
                    statement.setString(6, item.configurationJson());
                }
                statement.executeUpdate();
            }
        }
    }

    private static List<OrderItemDto> items(Connection connection, String orderId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT order_id, line_number, material_id, quantity, unit_price, configuration_json "
                        + "FROM order_items WHERE order_id = ? ORDER BY line_number")) {
            statement.setString(1, orderId);
            try (ResultSet rs = statement.executeQuery()) {
                List<OrderItemDto> result = new ArrayList<>();
                while (rs.next()) {
                    result.add(mapItem(rs));
                }
                return result;
            }
        }
    }

    private static OrderDto mapOrder(ResultSet rs, List<OrderItemDto> items) throws SQLException {
        return new OrderDto(rs.getString("order_id"), rs.getString("customer_id"), rs.getString("material_id"),
                rs.getDouble("width"), rs.getDouble("height"), rs.getDouble("depth"), rs.getString("design_notes"),
                rs.getString("status"), rs.getObject("created_at", OffsetDateTime.class).toInstant(), items);
    }

    private static OrderItemDto mapItem(ResultSet rs) throws SQLException {
        return new OrderItemDto(rs.getInt("line_number"), rs.getString("material_id"), rs.getInt("quantity"),
                rs.getBigDecimal("unit_price"), rs.getString("configuration_json"));
    }
}
