package cv04.tdg.dataaccess;

import java.time.Instant;

/** DTO of one cv04_orders row; the dimensions are plain columns. */
public record OrderDto(String orderId, String customerId, String materialId, double width, double height,
                       double depth, String designNotes, String status, Instant createdAt) {}
