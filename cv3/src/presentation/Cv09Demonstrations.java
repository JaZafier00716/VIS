package presentation;

import business.command.CommandLogEntry;
import business.command.ConfirmOrderCommand;
import business.command.OrderCommandExecutor;
import business.command.PrepareExpeditionCommand;
import business.command.StartProductionCommand;
import business.document.ConfirmationCreator;
import business.document.DeliveryNoteCreator;
import business.document.DocumentCreator;
import business.document.InvoiceCreator;
import business.document.OrderDocument;
import business.domainmodel.BillingAddress;
import business.domainmodel.Order;
import business.domainmodel.OrderSpecification;
import business.observer.ExpeditionObserver;
import business.observer.ProductionObserver;
import business.pricing.CompanyPricing;
import business.pricing.IPricingStrategy;
import business.pricing.StandardPricing;
import business.service.OrderService;
import business.service.numbering.OrderNumberGenerator;
import configuration.ApplicationContext;
import configuration.CustomerMapping;
import configuration.ShippingMode;
import configuration.StorageType;

import java.io.PrintStream;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * CV09 test scenarios in a fresh store in {@code data/demo/cv09}: Singleton (order numbers), Strategy (price),
 * Observer (production and expedition), Command (status operations with a log) and Factory Method (documents).
 * The presentation layer only picks the command, strategy or creator and prints the result.
 */
public class Cv09Demonstrations {
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss");

    private final PrintStream out;
    private final StorageType storageType;
    private final CustomerMapping mapping;
    private final Path dir;

    public Cv09Demonstrations(PrintStream out, StorageType storageType, CustomerMapping mapping, Path dir) {
        this.out = out;
        this.storageType = storageType;
        this.mapping = mapping;
        this.dir = dir;
    }

    public static Cv09Demonstrations standard(PrintStream out, StorageType storageType, CustomerMapping mapping) {
        return new Cv09Demonstrations(out, storageType, mapping, Path.of("data", "demo", "cv09"));
    }

    public void runAll() {
        out.println();
        out.println("=== CV09 – návrhové vzory GoF (" + storageType + ", " + dir + ") ===");
        try {
            run();
        } catch (RuntimeException e) {
            out.println("  Neočekávaná chyba: " + e.getMessage());
        }
    }

    private void run() {
        ApplicationContext context = ApplicationContext.createFresh(storageType, mapping, dir);
        OrderService orders = context.orderService();
        context.customerService().createCompanyCustomer("C-001", "info@svoboda.cz", null,
                new BillingAddress("Dílenská 5", "Ostrava", "70900", "CZ"), "Truhlářství Svoboda s.r.o.",
                "25596641", "CZ25596641", "Petr Svoboda");
        context.materialService().createMaterial("M-OAK", "Dub");
        OrderCommandExecutor executor = new OrderCommandExecutor();

        out.println("  Singleton – OrderNumberGenerator:");
        out.println("    stejná instance: "
                + (OrderNumberGenerator.getInstance() == OrderNumberGenerator.getInstance()));
        StringBuilder numbers = new StringBuilder();
        for (int i = 0; i < 3; i++) {
            numbers.append(i == 0 ? "" : ", ").append(newOrder(orders, "Police").getNumber());
        }
        out.println("    tři nové objednávky dostaly čísla " + numbers);

        out.println("  Scénář A – objednávka a cena podle strategie:");
        Order order = newOrder(orders, "Jídelní stůl");
        orders.addItem(order.getOrderId(), "M-OAK", 1, new BigDecimal("1000.00"), null);
        orders.addItem(order.getOrderId(), "M-OAK", 1, new BigDecimal("1000.00"), null);
        String id = order.getOrderId();
        out.println("    vytvořena objednávka č. " + order.getNumber() + " (ID " + id + "), 2 položky po 1000 Kč");
        for (IPricingStrategy strategy : new IPricingStrategy[]{new StandardPricing(), new CompanyPricing()}) {
            out.println("    " + strategy.getName() + ": "
                    + context.orderPricingService(ShippingMode.STUB).calculatePrice(id, strategy));
        }

        out.println("  Scénář B – potvrzení příkazem, výroba je informována (Observer):");
        ProductionObserver production = new ProductionObserver(message -> out.println("    [výroba] " + message));
        orders.registerObserver(production);
        orders.registerObserver(new ExpeditionObserver(message -> out.println("    [expedice] " + message)));
        out.println("    " + describe(executor.execute(new ConfirmOrderCommand(orders, id))));
        out.println("    stav: " + orders.findOrder(id).getStatus().getLabel());
        printDocument(context, id, new ConfirmationCreator());

        out.println("  Scénář C – výroba a expedice:");
        out.println("    " + describe(executor.execute(new StartProductionCommand(orders, id))));
        out.println("    stav: " + orders.findOrder(id).getStatus().getLabel());
        out.println("    " + describe(executor.execute(new PrepareExpeditionCommand(orders, id))));
        out.println("    stav: " + orders.findOrder(id).getStatus().getLabel());
        printDocument(context, id, new DeliveryNoteCreator());
        out.println("    Rozšíření – nový typ dokumentu bez změny společné logiky:");
        printDocument(context, id, new InvoiceCreator());

        out.println("  Neplatný přechod – výroba bez potvrzení:");
        Order second = newOrder(orders, "Komoda");
        out.println("    " + describe(executor.execute(new StartProductionCommand(orders, second.getOrderId()))));
        out.println("    stav zůstal: " + orders.findOrder(second.getOrderId()).getStatus().getLabel());

        out.println("  Odhlášení pozorovatele výroby:");
        orders.unregisterObserver(production);
        Order third = newOrder(orders, "Skříň");
        out.println("    " + describe(executor.execute(new ConfirmOrderCommand(orders, third.getOrderId()))));
        out.println("    výroba nedostala žádnou zprávu (pozorovatel je odhlášen)");

        out.println("  Historie příkazů (čas, operace, objednávka, výsledek):");
        for (CommandLogEntry entry : executor.getLog()) {
            out.println("    " + describe(entry));
        }
    }

    private Order newOrder(OrderService orders, String notes) {
        return orders.createOrder(null, "C-001", "M-OAK",
                OrderSpecification.builder().width(120).height(75).depth(60).designNotes(notes).build());
    }

    private void printDocument(ApplicationContext context, String orderId, DocumentCreator creator) {
        OrderDocument document = context.orderDocumentService().createDocument(orderId, creator);
        out.println("    " + document.getTitle());
        out.println("    " + "=".repeat(document.getTitle().length()));
        document.getContent().lines().forEach(line -> out.println("    " + line));
    }

    /** One line of the command log, also used by the console menu. */
    public static String describe(CommandLogEntry entry) {
        LocalTime time = entry.time().atZone(ZoneId.systemDefault()).toLocalTime();
        return TIME.format(time) + "  " + entry.operation() + "  objednávka " + entry.orderId() + "  "
                + (entry.success() ? "OK" : "odmítnuto: " + entry.message());
    }
}
