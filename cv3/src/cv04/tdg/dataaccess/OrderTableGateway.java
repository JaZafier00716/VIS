package cv04.tdg.dataaccess;

import dataaccess.h2.H2Database;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Table Data Gateway for cv04_orders: CRUD only, no validation and no status rules (those are in the BL). */
public class OrderTableGateway {
    private static final String COLUMNS =
            "order_id, customer_id, material_id, width, height, depth, design_notes, status, created_at";
    private final H2Database database;

    public OrderTableGateway(H2Database database) {
        this.database = database;
    }

    public void insert(OrderDto order) {
        database.run("CV04 TDG: vložení objednávky", connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO cv04_orders (" + COLUMNS + ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
                statement.setString(1, order.orderId());
                statement.setString(2, order.customerId());
                statement.setString(3, order.materialId());
                statement.setDouble(4, order.width());
                statement.setDouble(5, order.height());
                statement.setDouble(6, order.depth());
                statement.setString(7, order.designNotes());
                statement.setString(8, order.status());
                statement.setObject(9, order.createdAt().atOffset(ZoneOffset.UTC));
                return statement.executeUpdate();
            }
        });
    }

    public void update(OrderDto order) {
        database.run("CV04 TDG: úprava objednávky", connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "UPDATE cv04_orders SET customer_id = ?, material_id = ?, width = ?, height = ?, depth = ?, "
                            + "design_notes = ?, status = ? WHERE order_id = ?")) {
                statement.setString(1, order.customerId());
                statement.setString(2, order.materialId());
                statement.setDouble(3, order.width());
                statement.setDouble(4, order.height());
                statement.setDouble(5, order.depth());
                statement.setString(6, order.designNotes());
                statement.setString(7, order.status());
                statement.setString(8, order.orderId());
                return statement.executeUpdate();
            }
        });
    }

    public Optional<OrderDto> findById(String orderId) {
        return database.run("CV04 TDG: načtení objednávky", connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT " + COLUMNS + " FROM cv04_orders WHERE order_id = ?")) {
                statement.setString(1, orderId);
                try (ResultSet rs = statement.executeQuery()) {
                    return rs.next() ? Optional.of(read(rs)) : Optional.empty();
                }
            }
        });
    }

    public List<OrderDto> findAll() {
        return database.run("CV04 TDG: načtení objednávek", connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT " + COLUMNS + " FROM cv04_orders ORDER BY order_id");
                 ResultSet rs = statement.executeQuery()) {
                List<OrderDto> orders = new ArrayList<>();
                while (rs.next()) {
                    orders.add(read(rs));
                }
                return orders;
            }
        });
    }

    private static OrderDto read(ResultSet rs) throws SQLException {
        return new OrderDto(rs.getString("order_id"), rs.getString("customer_id"), rs.getString("material_id"),
                rs.getDouble("width"), rs.getDouble("height"), rs.getDouble("depth"), rs.getString("design_notes"),
                rs.getString("status"), rs.getObject("created_at", OffsetDateTime.class).toInstant());
    }
}
