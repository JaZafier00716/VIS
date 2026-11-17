package business.service;

import business.domainmodel.Order;
import business.domainmodel.OrderSpecification;
import configuration.ApplicationContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import support.IsolatedJsonStorage;
import support.TestCustomers;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** CV06 - Foreign Key Mapping: Order -> Customer and Order -> Material are stored as ID columns. */
class ForeignKeyMappingTest {
    @TempDir
    Path dir;
    private IsolatedJsonStorage storage;

    @BeforeEach
    void setUp() {
        storage = new IsolatedJsonStorage(dir);
        ApplicationContext context = storage.newContext();
        TestCustomers.person(context.customerService(), "C-001", "Jana", "jana@example.com");
        context.materialService().createMaterial("M-OAK", "Dub");
    }

    @AfterEach
    void tearDown() {
        storage.close();
    }

    @Test
    void orderStoresTheCustomerKeyAndResolvesItOnLoad() {
        storage.newContext().orderService().createOrder("O-001", "C-001", "M-OAK", spec());

        Order reloaded = storage.newContext().orderService().findOrder("O-001");

        assertEquals("C-001", reloaded.getCustomerId());
        assertEquals("M-OAK", reloaded.getMaterialId());
        assertEquals("Jana Test", reloaded.getCustomer().getDisplayName());
    }

    @Test
    void referenceToAMissingCustomerOrMaterialIsRejected() {
        OrderService orders = storage.newContext().orderService();

        assertThrows(IllegalArgumentException.class, () -> orders.createOrder("O-1", "C-404", "M-OAK", spec()));
        assertThrows(IllegalArgumentException.class, () -> orders.createOrder("O-2", "C-001", "M-404", spec()));
    }

    @Test
    void customerWithOrdersCannotBeDeleted() {
        storage.newContext().orderService().createOrder("O-001", "C-001", "M-OAK", spec());

        assertThrows(RuntimeException.class, () -> storage.newContext().customerService().deleteCustomer("C-001"));
        assertEquals("Jana Test", storage.newContext().customerService().findCustomer("C-001").getDisplayName());
    }

    @Test
    void addressesAreFoundThroughTheirCustomerKey() {
        storage.newContext().customerService().addAddress("C-001", "Hlavní 1", "Ostrava", "70800", "CZ");

        assertEquals(1, storage.newContext().customerService().findCustomer("C-001").getAddresses().size());
    }

    private static OrderSpecification spec() {
        return OrderSpecification.builder().width(100).height(80).depth(50).build();
    }
}
