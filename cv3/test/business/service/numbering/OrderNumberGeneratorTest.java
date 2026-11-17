package business.service.numbering;

import business.domainmodel.Order;
import business.domainmodel.OrderSpecification;
import configuration.ApplicationContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import support.IsolatedJsonStorage;
import support.TestCustomers;

import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** CV09 Singleton: one generator per run, consecutive numbers for new orders. */
class OrderNumberGeneratorTest {
    @TempDir
    Path dir;

    @Test
    void getInstanceAlwaysReturnsTheSameInstance() {
        assertSame(OrderNumberGenerator.getInstance(), OrderNumberGenerator.getInstance());
    }

    @Test
    void constructorIsPrivate() {
        Constructor<?>[] constructors = OrderNumberGenerator.class.getDeclaredConstructors();
        assertEquals(1, constructors.length);
        assertTrue(Modifier.isPrivate(constructors[0].getModifiers()));
    }

    @Test
    void threeNewOrdersGetThreeConsecutiveNumbers() {
        try (IsolatedJsonStorage storage = new IsolatedJsonStorage(dir)) {
            ApplicationContext context = storage.newContext();
            TestCustomers.person(context.customerService(), "C-001", "Jana", "jana@example.com");
            context.materialService().createMaterial("M-OAK", "Dub");

            // Other tests share the singleton, so only the step between the numbers is known.
            int first = context.orderService().createOrder(null, "C-001", "M-OAK", spec()).getNumber();
            Order second = context.orderService().createOrder(null, "C-001", "M-OAK", spec());
            Order third = context.orderService().createOrder(null, "C-001", "M-OAK", spec());

            assertEquals(first + 1, second.getNumber());
            assertEquals(first + 2, third.getNumber());
            assertTrue(first >= 1001);
            // The number lives in memory only: a reloaded order has none.
            assertNull(storage.newContext().orderService().findOrder(third.getOrderId()).getNumber());
            assertThrows(IllegalStateException.class, () -> third.assignNumber(1));
        }
    }

    private static OrderSpecification spec() {
        return OrderSpecification.builder().width(120).height(75).depth(60).designNotes("Stůl").build();
    }
}
