package cv04.rdg.dataaccess;

import dataaccess.h2.H2Database;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Row Data Gateway: one object = one cv04_orders row. As the slide allows (RDG step 5), {@link #insert()}
 * does the minimal checks itself: dimensions &gt; 0 and an existing material.
 */
public class OrderRowGateway {
    private static final String COLUMNS =
            "order_id, customer_id, material_id, width, height, depth, design_notes, status, created_at";
    private final H2Database database;
    private final String orderId;
    private final String customerId;
    private final String materialId;
    private double width;
    private double height;
    private double depth;
    private String designNotes;
    private String status;
    private final Instant createdAt;

    public OrderRowGateway(H2Database database, String orderId, String customerId, String materialId, double width,
                           double height, double depth, String designNotes, String status, Instant createdAt) {
        this.database = database;
        this.orderId = orderId;
        this.customerId = customerId;
        this.materialId = materialId;
        this.width = width;
        this.height = height;
        this.depth = depth;
        this.designNotes = designNotes;
        this.status = status;
        this.createdAt = createdAt;
    }

    public static Optional<OrderRowGateway> find(H2Database database, String orderId) {
        return database.run("CV04 RDG: načtení objednávky", connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT " + COLUMNS + " FROM cv04_orders WHERE order_id = ?")) {
                statement.setString(1, orderId);
                try (ResultSet rs = statement.executeQuery()) {
                    return rs.next() ? Optional.of(read(database, rs)) : Optional.empty();
                }
            }
        });
    }

    public static List<OrderRowGateway> findAll(H2Database database) {
        return database.run("CV04 RDG: načtení objednávek", connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT " + COLUMNS + " FROM cv04_orders ORDER BY order_id");
                 ResultSet rs = statement.executeQuery()) {
                List<OrderRowGateway> rows = new ArrayList<>();
                while (rs.next()) {
                    rows.add(read(database, rs));
                }
                return rows;
            }
        });
    }

    public void insert() {
        if (width <= 0 || height <= 0 || depth <= 0) {
            throw new IllegalArgumentException("Rozměry musí být větší než 0.");
        }
        if (MaterialRowGateway.find(database, materialId).isEmpty()) {
            throw new IllegalArgumentException("Materiál " + materialId + " neexistuje.");
        }
        database.run("CV04 RDG: vložení objednávky", connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO cv04_orders (" + COLUMNS + ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
                statement.setString(1, orderId);
                statement.setString(2, customerId);
                statement.setString(3, materialId);
                statement.setDouble(4, width);
                statement.setDouble(5, height);
                statement.setDouble(6, depth);
                statement.setString(7, designNotes);
                statement.setString(8, status);
                statement.setObject(9, createdAt.atOffset(ZoneOffset.UTC));
                return statement.executeUpdate();
            }
        });
    }

    public void update() {
        database.run("CV04 RDG: úprava objednávky", connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "UPDATE cv04_orders SET width = ?, height = ?, depth = ?, design_notes = ?, status = ? "
                            + "WHERE order_id = ?")) {
                statement.setDouble(1, width);
                statement.setDouble(2, height);
                statement.setDouble(3, depth);
                statement.setString(4, designNotes);
                statement.setString(5, status);
                statement.setString(6, orderId);
                return statement.executeUpdate();
            }
        });
    }

    public void delete() {
        database.run("CV04 RDG: smazání objednávky", connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "DELETE FROM cv04_orders WHERE order_id = ?")) {
                statement.setString(1, orderId);
                return statement.executeUpdate();
            }
        });
    }

    private static OrderRowGateway read(H2Database database, ResultSet rs) throws SQLException {
        return new OrderRowGateway(database, rs.getString("order_id"), rs.getString("customer_id"),
                rs.getString("material_id"), rs.getDouble("width"), rs.getDouble("height"), rs.getDouble("depth"),
                rs.getString("design_notes"), rs.getString("status"),
                rs.getObject("created_at", OffsetDateTime.class).toInstant());
    }

    public String getOrderId() { return orderId; }
    public String getCustomerId() { return customerId; }
    public String getMaterialId() { return materialId; }
    public double getWidth() { return width; }
    public void setWidth(double width) { this.width = width; }
    public double getHeight() { return height; }
    public void setHeight(double height) { this.height = height; }
    public double getDepth() { return depth; }
    public void setDepth(double depth) { this.depth = depth; }
    public String getDesignNotes() { return designNotes; }
    public void setDesignNotes(String designNotes) { this.designNotes = designNotes; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Instant getCreatedAt() { return createdAt; }
}
