package dataaccess.mapper;

import dataaccess.dto.OrderDto;
import dataaccess.dto.OrderItemDto;
import business.domainmodel.Money;
import business.domainmodel.Order;
import business.domainmodel.OrderSpecification;
import business.domainmodel.OrderStatus;

import java.util.List;

public final class OrderMapper {
    private OrderMapper() {}
    public static Order toDomain(OrderDto dto) {
        // Embedded Value: the specification is rebuilt from columns of the order record.
        OrderSpecification specification = OrderSpecification.builder().width(dto.width()).height(dto.height())
                .depth(dto.depth()).designNotes(dto.designNotes()).build();
        // The enum is mapped by name; the order is reconstituted without business transitions.
        List<Order.StoredItem> items = dto.items().stream()
                .map(item -> new Order.StoredItem(item.lineNumber(), item.materialId(), item.quantity(),
                        Money.czk(item.unitPrice()), ProductConfigurationSerializer.deserialize(item.configurationJson())))
                .toList();
        return Order.restore(dto.orderId(), dto.customerId(), dto.materialId(), specification, dto.createdAt(),
                OrderStatus.valueOf(dto.status()), items);
    }
    public static OrderDto toDto(Order order) {
        OrderSpecification spec = order.getSpecification();
        List<OrderItemDto> items = order.getItems().stream()
                .map(item -> new OrderItemDto(item.getLineNumber(), item.getMaterialId(), item.getQuantity(),
                        item.getUnitPrice().amount(), ProductConfigurationSerializer.serialize(item.getConfiguration())))
                .toList();
        return new OrderDto(order.getOrderId(), order.getCustomerId(), order.getMaterialId(),
                spec.getWidth(), spec.getHeight(), spec.getDepth(), spec.getDesignNotes(),
                order.getStatus().name(), order.getCreatedAt(), items);
    }
}
