package business.pricing;

import business.domainmodel.Money;
import business.domainmodel.OrderSpecification;
import business.service.OrderPricingService;
import configuration.ApplicationContext;
import configuration.ShippingMode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import support.IsolatedJsonStorage;
import support.TestCustomers;

import java.math.BigDecimal;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** CV09 Strategy in the service: OrderPricingService loads the order and delegates to the chosen strategy. */
class OrderPricingStrategyServiceTest {
    @TempDir
    Path dir;
    private IsolatedJsonStorage storage;

    @BeforeEach
    void setUp() {
        storage = new IsolatedJsonStorage(dir);
        ApplicationContext context = storage.newContext();
        TestCustomers.person(context.customerService(), "C-001", "Jana", "jana@example.com");
        context.materialService().createMaterial("M-OAK", "Dub");
        OrderSpecification spec = OrderSpecification.builder().width(100).height(80).depth(50).build();
        context.orderService().createOrder("O-001", "C-001", "M-OAK", spec);
        context.orderService().addItem("O-001", "M-OAK", 1, new BigDecimal("1000.00"), null);
        context.orderService().addItem("O-001", "M-OAK", 1, new BigDecimal("1000.00"), null);
        context.orderService().createOrder("O-EMPTY", "C-001", "M-OAK", spec);
    }

    @AfterEach
    void tearDown() {
        storage.close();
    }

    private OrderPricingService pricing() {
        return storage.newContext().orderPricingService(ShippingMode.STUB);
    }

    @Test
    void theSameStoredOrderIsPricedByBothStrategies() {
        assertEquals(Money.czk(2000), pricing().calculatePrice("O-001", new StandardPricing()));
        assertEquals(Money.czk(1800), pricing().calculatePrice("O-001", new CompanyPricing()));
    }

    @Test
    void cv08CalculationStillUsesTheStandardItemsTotal() {
        assertEquals(Money.czk(2000), pricing().calculate("O-001", null).itemsTotal());
    }

    @Test
    void invalidInputIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> pricing().calculatePrice("O-EMPTY", new CompanyPricing()));
        assertThrows(IllegalArgumentException.class, () -> pricing().calculatePrice("O-001", null));
        assertThrows(IllegalArgumentException.class, () -> pricing().calculatePrice("O-404", new StandardPricing()));
    }
}
