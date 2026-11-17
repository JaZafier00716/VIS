package business.service;

import business.domainmodel.Money;
import business.domainmodel.Order;
import business.domainmodel.OrderItem;
import business.domainmodel.OrderSpecification;
import com.fasterxml.jackson.core.type.TypeReference;
import configuration.ApplicationContext;
import dataaccess.persistence.JsonFiles;
import dataaccess.row.OrderItemRow;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import support.IsolatedJsonStorage;
import support.TestCustomers;

import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** CV06 - Dependent Mapping: order items are saved, replaced and deleted only through their order. */
class DependentMappingTest {
    @TempDir
    Path dir;
    private IsolatedJsonStorage storage;

    @BeforeEach
    void setUp() {
        storage = new IsolatedJsonStorage(dir);
        ApplicationContext context = storage.newContext();
        TestCustomers.person(context.customerService(), "C-001", "Jana", "jana@example.com");
        context.materialService().createMaterial("M-OAK", "Dub");
        context.orderService().createOrder("O-001", "C-001", "M-OAK",
                OrderSpecification.builder().width(100).height(80).depth(50).build());
    }

    @AfterEach
    void tearDown() {
        storage.close();
    }

    @Test
    void itemsAreSavedAndLoadedWithTheirOrder() {
        OrderService orders = storage.newContext().orderService();
        orders.addItem("O-001", "M-OAK", 1, new BigDecimal("1000.00"), null);
        orders.addItem("O-001", "M-OAK", 3, new BigDecimal("250.50"), null);

        Order reloaded = storage.newContext().orderService().findOrder("O-001");

        assertEquals(List.of(1, 2), reloaded.getItems().stream().map(OrderItem::getLineNumber).toList());
        assertEquals(Money.czk(new BigDecimal("250.50")), reloaded.getItems().get(1).getUnitPrice());
    }

    @Test
    void changedAndRemovedItemsReplaceTheStoredOnes() {
        OrderService orders = storage.newContext().orderService();
        orders.addItem("O-001", "M-OAK", 1, new BigDecimal("1000.00"), null);
        orders.addItem("O-001", "M-OAK", 3, new BigDecimal("250.00"), null);
        orders.changeItemQuantity("O-001", 2, 5);
        orders.removeItem("O-001", 1);

        List<OrderItemRow> stored = itemRows();
        assertEquals(1, stored.size());
        assertEquals(2, stored.get(0).lineNumber());
        assertEquals(5, stored.get(0).quantity());
    }

    @Test
    void deletingTheOrderDeletesItsItems() {
        storage.newContext().orderService().addItem("O-001", "M-OAK", 1, new BigDecimal("1.00"), null);

        storage.newContext().orderService().deleteOrder("O-001");

        assertTrue(itemRows().isEmpty());
    }

    @Test
    void itemsCannotBeCreatedOutsideTheOrder() {
        Constructor<?>[] constructors = OrderItem.class.getDeclaredConstructors();
        assertTrue(Arrays.stream(constructors).noneMatch(c -> Modifier.isPublic(c.getModifiers())));
        assertThrows(IllegalArgumentException.class,
                () -> storage.newContext().orderService().addItem("O-001", "M-OAK", 0, BigDecimal.ONE, null));
    }

    private static List<OrderItemRow> itemRows() {
        return JsonFiles.read("order_items.json", new TypeReference<List<OrderItemRow>>() {});
    }
}
