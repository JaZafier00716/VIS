package dataaccess.dto;

import java.time.Instant;
import java.util.List;

public record OrderDto(String orderId, String customerId, String materialId,
                       double width, double height, double depth, String designNotes,
                       String status, Instant createdAt, List<OrderItemDto> items) {
    public OrderDto {
        items = items == null ? List.of() : List.copyOf(items);
    }
}
