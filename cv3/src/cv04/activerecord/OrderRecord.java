package cv04.activerecord;

import cv04.Cv04Schema;
import dataaccess.h2.H2Database;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

/**
 * Active Record for orders (cv04 AR steps 1–5): data, domain rules ({@link #validate()}, {@link #confirm()})
 * and the SQL ({@link #save()}, {@link #delete()}, {@link #find}) in one class. {@code save()} calls
 * {@code validate()} first.
 */
public class OrderRecord {
    private static final String COLUMNS =
            "order_id, customer_id, material_id, width, height, depth, design_notes, status, created_at";
    private final H2Database database;
    private final String orderId;
    private final String customerId;
    private final String materialId;
    private final double width;
    private final double height;
    private final double depth;
    private final String designNotes;
    private String status;
    private final Instant createdAt;

    private OrderRecord(H2Database database, String orderId, String customerId, String materialId, double width,
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

    /** A new order in state CEKA_NA_POTVRZENI; nothing is stored until {@link #save()}. */
    public static OrderRecord create(H2Database database, String customerId, String materialId, double width,
                                     double height, double depth, String designNotes) {
        return new OrderRecord(database, "O-" + UUID.randomUUID(), customerId, materialId, width, height, depth,
                designNotes, Cv04Schema.STATUS_WAITING, Instant.now());
    }

    /**
     * The CV04 use case with Active Record: the objects validate themselves before anything is saved
     * (Zakaznik.Save(), then Objednavka.Save()), so a rejected order stores nothing.
     */
    public static OrderRecord place(H2Database database, String customerName, String email, String materialId,
                                    double width, double height, double depth, String designNotes) {
        CustomerRecord customer = new CustomerRecord(database, "C-" + UUID.randomUUID(), customerName, email);
        OrderRecord order = create(database, customer.getCustomerId(), materialId, width, height, depth,
                designNotes);
        customer.validate();
        order.validate();
        customer.save();
        order.save();
        return find(database, order.getOrderId()).orElseThrow();
    }

    public static Optional<OrderRecord> find(H2Database database, String orderId) {
        return database.run("CV04 AR: načtení objednávky", connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT " + COLUMNS + " FROM cv04_orders WHERE order_id = ?")) {
                statement.setString(1, orderId);
                try (ResultSet rs = statement.executeQuery()) {
                    if (!rs.next()) {
                        return Optional.empty();
                    }
                    return Optional.of(new OrderRecord(database, rs.getString("order_id"),
                            rs.getString("customer_id"), rs.getString("material_id"), rs.getDouble("width"),
                            rs.getDouble("height"), rs.getDouble("depth"), rs.getString("design_notes"),
                            rs.getString("status"), rs.getObject("created_at", OffsetDateTime.class).toInstant()));
                }
            }
        });
    }

    public void validate() {
        if (width <= 0 || height <= 0 || depth <= 0) {
            throw new IllegalArgumentException("Rozměry musí být větší než 0.");
        }
        if (customerId == null || customerId.isBlank()) {
            throw new IllegalArgumentException("Objednávka musí mít zákazníka.");
        }
        if (!MaterialRecord.exists(database, materialId)) {
            throw new IllegalArgumentException("Materiál " + materialId + " neexistuje.");
        }
    }

    public void confirm() {
        if (!Cv04Schema.STATUS_WAITING.equals(status)) {
            throw new IllegalStateException("Objednávku nelze potvrdit v aktuálním stavu.");
        }
        status = "POTVRZENA";
    }

    /** Validate(), then INSERT or UPDATE by ID. */
    public void save() {
        validate();
        database.run("CV04 AR: uložení objednávky", connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "MERGE INTO cv04_orders (" + COLUMNS + ") KEY (order_id) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
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

    public void delete() {
        database.run("CV04 AR: smazání objednávky", connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "DELETE FROM cv04_orders WHERE order_id = ?")) {
                statement.setString(1, orderId);
                return statement.executeUpdate();
            }
        });
    }

    public String getOrderId() { return orderId; }
    public String getCustomerId() { return customerId; }
    public String getMaterialId() { return materialId; }
    public double getWidth() { return width; }
    public double getHeight() { return height; }
    public double getDepth() { return depth; }
    public String getDesignNotes() { return designNotes; }
    public String getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
}
