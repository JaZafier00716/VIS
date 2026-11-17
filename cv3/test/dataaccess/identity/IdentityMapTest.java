package dataaccess.identity;

import business.domainmodel.Customer;
import business.domainmodel.Order;
import business.domainmodel.OrderSpecification;
import business.domainmodel.PersonCustomer;
import org.junit.jupiter.api.Test;
import support.TestCustomers;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class IdentityMapTest {

    @Test
    void customerAndOrderWithTheSameIdDoNotCollide() {
        IdentityMap map = new IdentityMap();
        PersonCustomer customer = TestCustomers.person("1", "Jana", "jana@example.com");
        Order order = new Order("1", "1", "M-1", OrderSpecification.builder().width(1).height(1).depth(1).build(),
                Instant.now());

        map.put(customer, "1");
        map.put(order, "1");

        assertNotEquals(map.keyFor(Customer.class, "1"), map.keyFor(Order.class, "1"));
        assertSame(customer, map.get(Customer.class, "1").orElseThrow());
        assertSame(order, map.get(Order.class, "1").orElseThrow());
    }

    @Test
    void aSubclassIsFoundUnderItsHierarchyRoot() {
        IdentityMap map = new IdentityMap();
        PersonCustomer customer = TestCustomers.person("1", "Jana", "jana@example.com");

        map.put(customer, "1");

        assertSame(customer, map.get(Customer.class, "1").orElseThrow());
        assertSame(customer, map.get(PersonCustomer.class, "1").orElseThrow());
    }
}
