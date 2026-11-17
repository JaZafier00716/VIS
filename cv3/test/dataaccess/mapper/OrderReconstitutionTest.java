package dataaccess.mapper;

import business.domainmodel.Order;
import business.domainmodel.OrderItem;
import business.domainmodel.OrderStatus;
import dataaccess.dto.OrderDto;
import dataaccess.dto.OrderItemDto;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Loading an order reconstitutes the stored state (Order.restore); it does not replay business transitions. */
class OrderReconstitutionTest {

    @Test
    void confirmedOrderIsLoadedAsStoredWithoutBusinessTransitions() {
        OrderDto stored = dto(OrderStatus.POTVRZENA, List.of(
                new OrderItemDto(2, "M-1", 1, BigDecimal.ONE, null),
                new OrderItemDto(5, "M-1", 3, BigDecimal.TEN, null)));

        Order order = OrderMapper.toDomain(stored);

        assertEquals(OrderStatus.POTVRZENA, order.getStatus());
        // addItem() would renumber the lines 1, 2; restore keeps the stored gaps.
        assertEquals(List.of(2, 5), order.getItems().stream().map(OrderItem::getLineNumber).toList());
        // The business transition is still guarded: a loaded confirmed order cannot be confirmed again.
        assertThrows(IllegalStateException.class, order::confirm);
    }

    @ParameterizedTest
    @EnumSource(OrderStatus.class)
    void everyStatusRoundTripsByName(OrderStatus status) {
        OrderDto stored = dto(status, List.of());

        assertEquals(stored, OrderMapper.toDto(OrderMapper.toDomain(stored)));
    }

    private static OrderDto dto(OrderStatus status, List<OrderItemDto> items) {
        return new OrderDto("O-1", "C-1", "M-1", 100, 80, 50, null, status.name(),
                Instant.parse("2026-10-27T10:00:00Z"), items);
    }
}
