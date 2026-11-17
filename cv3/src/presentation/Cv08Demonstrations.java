package presentation;

import business.domainmodel.DiscountPolicy;
import business.domainmodel.Money;
import business.domainmodel.NoDiscount;
import business.domainmodel.Order;
import business.domainmodel.OrderSpecification;
import business.domainmodel.PercentageDiscount;
import business.service.OrderPriceSummary;
import business.service.PlaceOrderService.AddressData;
import business.service.PlaceOrderService.CustomerData;
import configuration.ApplicationContext;
import configuration.CustomerMapping;
import configuration.ShippingMode;
import configuration.StorageType;

import java.io.PrintStream;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.LocalDate;

/**
 * CV08 test scenarios (cv08 p.19) in a fresh store in {@code data/demo/cv08}: Layer Supertype, Value Object,
 * Special Case, and the price calculation of one order in both shipping modes (Service Stub, Gateway + Mapper).
 * The presentation layer only calls services and value objects.
 */
public class Cv08Demonstrations {
    private final PrintStream out;
    private final StorageType storageType;
    private final CustomerMapping mapping;
    private final Path dir;

    public Cv08Demonstrations(PrintStream out, StorageType storageType, CustomerMapping mapping, Path dir) {
        this.out = out;
        this.storageType = storageType;
        this.mapping = mapping;
        this.dir = dir;
    }

    public static Cv08Demonstrations standard(PrintStream out, StorageType storageType, CustomerMapping mapping) {
        return new Cv08Demonstrations(out, storageType, mapping, Path.of("data", "demo", "cv08"));
    }

    public void runAll() {
        out.println();
        out.println("=== CV08 – vzory pro širší použití (" + storageType + ", " + dir + ") ===");
        try {
            run();
        } catch (RuntimeException e) {
            out.println("  Neočekávaná chyba: " + e.getMessage());
        }
    }

    private void run() {
        ApplicationContext context = ApplicationContext.createFresh(storageType, mapping, dir);
        context.materialService().createMaterial("M-OAK", "Dub");
        Order order = context.placeOrderService().placeOrder(new CustomerData.NewPerson("jana@example.com", null,
                null, "Jana", "Nováková", LocalDate.of(1990, 5, 14)),
                new AddressData("Dílenská 5", "Ostrava", "70900", "CZ"), "M-OAK",
                OrderSpecification.builder().width(120).height(75).depth(60).designNotes("Jídelní stůl").build());

        context.orderService().addItem(order.getOrderId(), "M-OAK", 1, new BigDecimal("1500.00"), null);
        context.orderService().addItem(order.getOrderId(), "M-OAK", 1, new BigDecimal("500.00"), null);

        // A separate unit of work, so the in-memory change below does not reach the price calculation.
        out.println("  Layer Supertype – změna objednávky (BaseEntity.markUpdated):");
        Order loaded = ApplicationContext.create(storageType, mapping, dir).orderService().findOrder(order.getOrderId());
        out.println("    vytvořeno " + loaded.getCreatedAt() + ", změněno " + loaded.getUpdatedAt());
        loaded.changeItemQuantity(1, 2);
        out.println("    po změně množství změněno " + loaded.getUpdatedAt()
                + " (UpdatedAt je jen v doménovém modelu, neukládá se)");

        out.println("  Value Object – Money:");
        out.println("    Money(500, CZK) = Money(500.00, CZK): "
                + Money.czk(500).equals(Money.czk(new BigDecimal("500.00"))));
        try {
            Money.czk(-1);
            out.println("    záporná cena přijata (chyba)");
        } catch (IllegalArgumentException e) {
            out.println("    záporná cena odmítnuta: " + e.getMessage());
        }

        for (ShippingMode mode : ShippingMode.values()) {
            out.println("  Kalkulace v režimu " + mode + ":");
            for (DiscountPolicy discount : new DiscountPolicy[]{NoDiscount.INSTANCE, new PercentageDiscount(10)}) {
                printSummary(out, context.orderPricingService(mode).calculate(order.getOrderId(), discount));
            }
        }
    }

    /** The recapitulation (cv08 p.3 step 5), also used by the console menu. */
    public static void printSummary(PrintStream out, OrderPriceSummary summary) {
        out.println("    Objednávka " + summary.orderId() + " (" + summary.pieces() + " ks): položky "
                + summary.itemsTotal() + ", po slevě (" + summary.discount().describe() + ") "
                + summary.afterDiscount() + ", doprava " + summary.shipping().price() + " / "
                + summary.shipping().deliveryDays() + " dny, celkem " + summary.total());
    }
}
