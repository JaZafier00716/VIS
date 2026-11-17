package business.domainmodel;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** CV09: the order life cycle allows only the next status; a rejected change leaves nothing pending. */
class OrderStatusTransitionTest {
    @Test
    void fullLifeCycleInOrder() {
        Order order = order();
        order.confirm();
        order.startProduction();
        order.prepareForExpedition();
        order.ship();
        order.close();
        assertEquals(OrderStatus.UZAVRENA, order.getStatus());
        assertNull(OrderStatus.UZAVRENA.next());
        assertEquals("Nová", OrderStatus.CEKA_NA_POTVRZENI.getLabel());
    }

    @Test
    void skippingAStatusIsRejectedAndNotAnnounced() {
        Order order = order();
        List<OrderStatus> heard = new ArrayList<>();
        order.registerObserver((o, oldStatus, newStatus) -> heard.add(newStatus));

        assertThrows(IllegalStateException.class, order::startProduction);
        assertThrows(IllegalStateException.class, order::close);
        order.publishStatusChanges();

        assertEquals(OrderStatus.CEKA_NA_POTVRZENI, order.getStatus());
        assertTrue(heard.isEmpty());
        assertFalse(OrderStatus.POTVRZENA.canChangeTo(OrderStatus.POTVRZENA));
    }

    @Test
    void pendingChangesArePublishedOnce() {
        Order order = order();
        List<OrderStatus> heard = new ArrayList<>();
        order.registerObserver((o, oldStatus, newStatus) -> heard.add(newStatus));

        order.confirm();
        assertTrue(heard.isEmpty(), "nothing is announced before publishing");
        order.publishStatusChanges();
        order.publishStatusChanges();

        assertEquals(List.of(OrderStatus.POTVRZENA), heard);
    }

    private static Order order() {
        return new Order("O-1", "C-1", "M-1",
                OrderSpecification.builder().width(120).height(75).depth(60).designNotes("Stůl").build(), null);
    }
}
