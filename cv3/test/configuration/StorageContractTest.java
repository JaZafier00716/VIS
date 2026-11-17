package configuration;

import business.domainmodel.Finish;
import business.domainmodel.Order;
import business.domainmodel.OrderItem;
import business.domainmodel.OrderSpecification;
import business.domainmodel.OrderStatus;
import business.domainmodel.ProductConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import support.TestCustomers;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The same behaviour is required from every storage: JSON files and H2 must be interchangeable behind
 * the gateway interfaces. Each subclass only says how to build the application.
 */
abstract class StorageContractTest {
    @TempDir
    Path dir;

    protected abstract ApplicationContext newContext(Path dataDirectory);

    private ApplicationContext context() {
        return newContext(dir);
    }

    private void seed() {
        ApplicationContext context = context();
        TestCustomers.person(context.customerService(), "C-001", "Jana", "jana@example.com");
        context.materialService().createMaterial("M-OAK", "Dub");
        context.materialService().createFinish("F-LACQ", "Lak");
    }

    @Test
    void identityFieldAndForeignKeyMapping() {
        seed();
        context().orderService().createOrder("O-001", "C-001", "M-OAK", spec());

        Order order = context().orderService().findOrder("O-001");
        assertEquals("O-001", order.getOrderId());
        assertEquals("Jana Test", order.getCustomer().getDisplayName());
        assertThrows(RuntimeException.class,
                () -> context().orderService().createOrder("O-002", "C-404", "M-OAK", spec()));
        assertThrows(RuntimeException.class, () -> context().customerService().deleteCustomer("C-001"));
    }

    @Test
    void associationTableMapping() {
        seed();
        context().materialService().addFinish("M-OAK", "F-LACQ");
        assertFalse(context().materialService().addFinish("M-OAK", "F-LACQ"));

        assertEquals(List.of("F-LACQ"),
                context().materialService().findFinishes("M-OAK").stream().map(Finish::getFinishId).toList());
        context().materialService().removeFinish("M-OAK", "F-LACQ");
        assertTrue(context().materialService().findFinishes("M-OAK").isEmpty());
    }

    @Test
    void dependentMappingEmbeddedValueAndSerializedLob() {
        seed();
        ProductConfiguration configuration = new ProductConfiguration("bílá", "CNC", Map.of("police", "4"));
        context().orderService().createOrder("O-001", "C-001", "M-OAK", spec());
        context().orderService().addItem("O-001", "M-OAK", 2, new BigDecimal("1500.00"), configuration);
        context().orderService().addItem("O-001", "M-OAK", 1, new BigDecimal("99.90"), null);
        context().orderService().removeItem("O-001", 1);
        context().orderService().confirmOrder("O-001");

        Order order = context().orderService().findOrder("O-001");
        assertEquals(spec(), order.getSpecification());
        assertEquals(OrderStatus.POTVRZENA, order.getStatus());
        assertEquals(List.of(2), order.getItems().stream().map(OrderItem::getLineNumber).toList());
        assertNull(order.getItems().get(0).getConfiguration());

        context().orderService().addItem("O-001", "M-OAK", 2, new BigDecimal("1500.00"), configuration);
        assertEquals(configuration, context().orderService().findOrder("O-001").getItems().get(1).getConfiguration());

        context().orderService().deleteOrder("O-001");
        assertTrue(context().orderService().findAll().isEmpty());
        context().customerService().deleteCustomer("C-001");
    }

    @Test
    void rejectedOperationLeavesNoData() {
        seed();
        assertThrows(RuntimeException.class,
                () -> context().customerService().addAddress("C-404", "Hlavní 1", "Ostrava", "70800", "CZ"));
        context().customerService().addAddress("C-001", "Hlavní 1", "Ostrava", "70800", "CZ");
        assertEquals(1, context().customerService().findCustomer("C-001").getAddresses().size());
    }

    private static OrderSpecification spec() {
        return OrderSpecification.builder().width(120).height(75).depth(60).designNotes("Stůl").build();
    }
}
