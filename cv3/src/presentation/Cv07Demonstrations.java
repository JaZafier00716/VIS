package presentation;

import business.domainmodel.BillingAddress;
import business.domainmodel.CompanyCustomer;
import business.domainmodel.Customer;
import business.domainmodel.Order;
import business.domainmodel.OrderSpecification;
import business.domainmodel.PersonCustomer;
import business.service.PlaceOrderService.AddressData;
import business.service.PlaceOrderService.CustomerData;
import configuration.ApplicationContext;
import configuration.CustomerMapping;
import configuration.StorageType;

import java.io.PrintStream;
import java.nio.file.Path;
import java.time.LocalDate;

/**
 * CV07 scenarios A and B (cv07 p.15) for each inheritance strategy, each in a fresh store in
 * {@code data/demo/cv07/<strategy>}. The user's choice is person or company; the strategy is the data access
 * layer's business, so the same calls run unchanged for CTI and STI. The presentation layer only calls services
 * and asks the composition root which tables were written; it never touches SQL or files.
 */
public class Cv07Demonstrations {
    private static final BillingAddress BILLING = new BillingAddress("Hlavní 1", "Ostrava", "70800", "CZ");
    private static final AddressData DELIVERY = new AddressData("Dílenská 5", "Ostrava", "70900", "CZ");
    private final PrintStream out;
    private final StorageType storageType;
    private final Path root;

    public Cv07Demonstrations(PrintStream out, StorageType storageType, Path root) {
        this.out = out;
        this.storageType = storageType;
        this.root = root;
    }

    public static Cv07Demonstrations standard(PrintStream out, StorageType storageType) {
        return new Cv07Demonstrations(out, storageType, Path.of("data", "demo", "cv07"));
    }

    public void runAll() {
        for (CustomerMapping mapping : CustomerMapping.values()) {
            Path dir = root.resolve(mapping.name().toLowerCase());
            out.println();
            out.println("=== Mapování dědičnosti " + mapping + " (" + storageType + ", " + dir + ") ===");
            try {
                run(mapping, dir);
            } catch (RuntimeException e) {
                out.println("  Neočekávaná chyba: " + e.getMessage());
            }
        }
    }

    private void run(CustomerMapping mapping, Path dir) {
        ApplicationContext context = ApplicationContext.createFresh(storageType, mapping, dir);
        context.materialService().createMaterial("M-OAK", "Dub");
        String where = storageType == StorageType.H2 ? "tabulky" : "soubory";
        String suffix = storageType == StorageType.H2 ? "" : ".json";

        // Scenario A: the user chooses the customer type and fills in the matching fields.
        out.println("  Scénář A – vytvoření objednávky:");
        Order personOrder = context.placeOrderService().placeOrder(new CustomerData.NewPerson("jana@example.com",
                "+420 777 123 456", BILLING, "Jana", "Nováková", LocalDate.of(1990, 5, 14)), DELIVERY, "M-OAK",
                spec());
        Order companyOrder = context.placeOrderService().placeOrder(new CustomerData.NewCompany("info@svoboda.cz",
                null, null, "Truhlářství Svoboda s.r.o.", "12345679", "CZ12345679", "Petr Svoboda"), DELIVERY,
                "M-OAK", spec());
        for (Order order : new Order[]{personOrder, companyOrder}) {
            out.println("    " + typeOf(order.getCustomer()) + " " + order.getCustomer().getDisplayName()
                    + " → objednávka " + order.getOrderId() + "; " + where + " zákazníka: "
                    + context.customerTables(order.getCustomerId()).stream().map(t -> t + suffix).toList());
        }

        // Scenario B: load the order by ID in a new unit of work; the DAL builds the right subclass.
        out.println("  Scénář B – načtení objednávky:");
        ApplicationContext fresh = ApplicationContext.create(storageType, mapping, dir);
        for (Order stored : new Order[]{personOrder, companyOrder}) {
            Order order = fresh.orderService().findOrder(stored.getOrderId());
            Customer customer = order.getCustomer();
            out.println("    " + order.getOrderId() + " → " + customer.getClass().getSimpleName() + ": "
                    + describe(customer));
        }
    }

    private static String typeOf(Customer customer) {
        return customer instanceof CompanyCustomer ? "Firma" : "Osoba";
    }

    private static String describe(Customer customer) {
        String common = customer.getEmail() + ", tel. " + (customer.getPhone() == null ? "–" : customer.getPhone())
                + ", fakturace "
                + (customer.getBillingAddress() == null ? "–" : customer.getBillingAddress().city());
        return switch (customer) {
            case PersonCustomer p -> p.getFirstName() + " " + p.getLastName() + ", nar. " + p.getBirthDate() + ", "
                    + common;
            case CompanyCustomer c -> c.getCompanyName() + ", IČO " + c.getIco() + ", DIČ " + c.getDic()
                    + ", kontakt " + c.getContactPerson() + ", " + common;
            default -> common;
        };
    }

    private static OrderSpecification spec() {
        return OrderSpecification.builder().width(120).height(75).depth(60).designNotes("Jídelní stůl").build();
    }
}
