package business.service;

import business.domainmodel.Money;
import business.domainmodel.NoDiscount;
import business.domainmodel.OrderSpecification;
import business.domainmodel.PercentageDiscount;
import business.domainmodel.ShippingQuote;
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

/** CV08 (cv08 p.17–19): the same order is priced in both shipping modes with and without a discount. */
class OrderPricingServiceTest {
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
        // 2 pieces, 2 000 Kč in total (the slides' example price)
        context.orderService().addItem("O-001", "M-OAK", 1, new BigDecimal("1500.00"), null);
        context.orderService().addItem("O-001", "M-OAK", 1, new BigDecimal("500.00"), null);
        context.orderService().createOrder("O-EMPTY", "C-001", "M-OAK", spec);
    }

    @AfterEach
    void tearDown() {
        storage.close();
    }

    @Test
    void gatewayModeWithoutDiscount() {
        OrderPriceSummary summary = storage.newContext().orderPricingService(ShippingMode.GATEWAY)
                .calculate("O-001", NoDiscount.INSTANCE);
        assertEquals(2, summary.pieces());
        assertEquals(Money.czk(2000), summary.itemsTotal());
        assertEquals(Money.czk(2000), summary.afterDiscount());
        assertEquals(new ShippingQuote(Money.czk(120), 2), summary.shipping());
        assertEquals(Money.czk(2120), summary.total());
    }

    @Test
    void stubModeWithTenPercentDiscount() {
        OrderPriceSummary summary = storage.newContext().orderPricingService(ShippingMode.STUB)
                .calculate("O-001", new PercentageDiscount(10));
        assertEquals(Money.czk(1800), summary.afterDiscount());
        assertEquals(new ShippingQuote(Money.czk(150), 3), summary.shipping());
        assertEquals(Money.czk(1950), summary.total());
    }

    @Test
    void theSameOrderWorksInBothModesAndOnlyShippingDiffers() {
        ApplicationContext context = storage.newContext();
        for (ShippingMode mode : ShippingMode.values()) {
            OrderPriceSummary summary = context.orderPricingService(mode).calculate("O-001", null);
            assertEquals(Money.czk(2000), summary.afterDiscount());
            assertEquals(NoDiscount.INSTANCE, summary.discount());
        }
    }

    @Test
    void orderWithoutItemsCannotBePriced() {
        assertThrows(IllegalArgumentException.class, () -> storage.newContext()
                .orderPricingService(ShippingMode.STUB).calculate("O-EMPTY", NoDiscount.INSTANCE));
    }

    @Test
    void shippingModeIsParsedFromTheCommandLine() {
        assertEquals(ShippingMode.STUB, ShippingMode.parse("stub"));
        assertEquals(ShippingMode.GATEWAY, ShippingMode.parse(null));
        assertThrows(IllegalArgumentException.class, () -> ShippingMode.parse("x"));
    }
}
