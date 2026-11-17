package business.domainmodel;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** CV08 Layer Supertype (cv08 p.6, p.19 "Změna objednávky → aktualizace UpdatedAt"). */
class LayerSupertypeTest {
    private static Order order() {
        OrderSpecification specification = OrderSpecification.builder().width(100).height(80).depth(40)
                .designNotes("dub").build();
        return new Order("O-1", "C-1", "M-OAK", specification, Instant.now().minus(1, ChronoUnit.HOURS));
    }

    @Test
    void changingAnOrderUpdatesUpdatedAt() {
        Order order = order();
        Instant before = order.getUpdatedAt();
        assertEquals(order.getCreatedAt(), before);

        order.addItem("M-OAK", 2, Money.czk(1000), null);

        assertTrue(order.getUpdatedAt().isAfter(before));
        assertEquals("O-1", order.getId());
        assertEquals(order.getId(), order.getOrderId());
    }

    @Test
    void everyBusinessChangeOfTheOrderMarksIt() {
        Order order = order();
        order.addItem("M-OAK", 2, Money.czk(1000), null);
        Instant afterAdd = order.getUpdatedAt();
        order.changeItemQuantity(1, 3);
        assertTrue(!order.getUpdatedAt().isBefore(afterAdd));
        order.confirm();
        assertTrue(!order.getUpdatedAt().isBefore(afterAdd));
    }

    @Test
    void restoringAnOrderIsNotAChange() {
        Instant created = Instant.now().minus(2, ChronoUnit.DAYS);
        Order order = Order.restore("O-1", "C-1", "M-OAK", order().getSpecification(), created,
                OrderStatus.POTVRZENA, java.util.List.of(new Order.StoredItem(1, "M-OAK", 1, Money.czk(new BigDecimal("10.00")), null)));
        assertEquals(created, order.getUpdatedAt());
    }

    @Test
    void allEntitiesShareTheSupertypeAndTheCustomerHierarchyStays() {
        Customer person = new PersonCustomer("C-1", "jan@example.com", null, null, "Jan", "Novák",
                LocalDate.of(1990, 1, 1));
        assertInstanceOf(Customer.class, person);
        assertInstanceOf(BaseEntity.class, person);
        assertEquals("C-1", person.getId());
        assertEquals(person.getId(), person.getCustomerId());
        assertInstanceOf(BaseEntity.class, Material.builder().materialId("M-OAK").name("Dub").build());
        assertInstanceOf(BaseEntity.class, Finish.builder().finishId("F-1").name("Lak").build());
        assertInstanceOf(BaseEntity.class, Address.builder().addressId("A-1").customerId("C-1").build());
    }
}
