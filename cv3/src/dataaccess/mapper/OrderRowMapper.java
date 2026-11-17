package dataaccess.mapper;

import dataaccess.dto.OrderDto;
import dataaccess.dto.OrderItemDto;
import dataaccess.row.OrderItemRow;
import dataaccess.row.OrderRow;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;

/** Splits an order aggregate into its order record and dependent item records, and joins them back. */
public final class OrderRowMapper {
    private OrderRowMapper() {}

    public static OrderRow toRow(OrderDto dto) {
        return new OrderRow(dto.orderId(), dto.customerId(), dto.materialId(), dto.width(), dto.height(),
                dto.depth(), dto.designNotes(), dto.status(), dto.createdAt().toString());
    }

    public static List<OrderItemRow> toItemRows(OrderDto dto) {
        return dto.items().stream()
                .map(item -> new OrderItemRow(dto.orderId(), item.lineNumber(), item.materialId(), item.quantity(),
                        item.unitPrice(), item.configurationJson()))
                .toList();
    }

    public static OrderDto toDto(OrderRow row, List<OrderItemRow> itemRows) {
        List<OrderItemDto> items = itemRows.stream()
                .filter(item -> item.orderId().equals(row.orderId()))
                .sorted(Comparator.comparingInt(OrderItemRow::lineNumber))
                .map(item -> new OrderItemDto(item.lineNumber(), item.materialId(), item.quantity(),
                        item.unitPrice(), item.configurationJson()))
                .toList();
        return new OrderDto(row.orderId(), row.customerId(), row.materialId(), row.width(), row.height(),
                row.depth(), row.designNotes(), row.status(), Instant.parse(row.createdAt()), items);
    }
}
