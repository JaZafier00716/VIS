package dataaccess.row;

/** JSON record of an order; the specification is embedded as plain fields (Embedded Value). */
public record OrderRow(String orderId, String customerId, String materialId, double width, double height,
                       double depth, String designNotes, String status, String createdAt) {}
