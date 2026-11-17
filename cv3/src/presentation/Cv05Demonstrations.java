package presentation;

import business.domainmodel.Address;
import business.domainmodel.BillingAddress;
import business.domainmodel.Customer;
import business.domainmodel.Order;
import business.domainmodel.OrderSpecification;
import business.service.CustomerService;
import business.service.PlaceOrderService;
import business.service.PlaceOrderService.AddressData;
import business.service.PlaceOrderService.CustomerData;
import configuration.ApplicationContext;
import configuration.CustomerMapping;
import configuration.StorageType;

import java.io.PrintStream;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.function.Consumer;

/**
 * Runs the three CV05 situations (cv05 p.3), each in a fresh storage in {@code data/demo/cv05/<scenario>}:
 * A Unit of Work, B Identity Map, C Lazy Load.
 * The presentation layer only calls services; it never touches SQL or files directly.
 */
public class Cv05Demonstrations {
    private static final BillingAddress BILLING = new BillingAddress("Hlavní 1", "Ostrava", "70800", "CZ");
    private final PrintStream out;
    private final StorageType storageType;
    private final CustomerMapping mapping;
    private final Path root;

    public Cv05Demonstrations(PrintStream out, StorageType storageType, CustomerMapping mapping, Path root) {
        this.out = out;
        this.storageType = storageType;
        this.mapping = mapping;
        this.root = root;
    }

    public static Cv05Demonstrations standard(PrintStream out, StorageType storageType, CustomerMapping mapping) {
        return new Cv05Demonstrations(out, storageType, mapping, Path.of("data", "demo", "cv05"));
    }

    public void runAll() {
        run("a-unit-of-work", "Unit of Work: zákazník + adresa + objednávka", this::unitOfWork);
        run("b-identity-map", "Identity Map: dvakrát stejný zákazník", this::identityMap);
        run("c-lazy-load", "Lazy Load: objednávka a odložený zákazník", this::lazyLoad);
    }

    private void run(String directory, String title, Consumer<Path> scenario) {
        Path dir = root.resolve(directory);
        out.println();
        out.println("=== " + title + " (" + storageType + "/" + mapping + ", " + dir + ") ===");
        ApplicationContext.createFresh(storageType, mapping, dir);
        try {
            scenario.accept(dir);
        } catch (RuntimeException e) {
            out.println("  Neočekávaná chyba: " + e.getMessage());
        }
    }

    private void unitOfWork(Path dir) {
        ApplicationContext.create(storageType, mapping, dir).materialService().createMaterial("M-OAK", "Dub");
        AddressData address = new AddressData("Hlavní 1", "Ostrava", "70800", "CZ");

        PlaceOrderService placeOrder = ApplicationContext.create(storageType, mapping, dir).placeOrderService();
        Order order = placeOrder.placeOrder(new CustomerData.NewPerson("jana@example.com", "+420 777 123 456", BILLING, "Jana", "Nováková",
                LocalDate.of(1990, 5, 14)), address,
                "M-OAK", spec(120, 75, 60, "Jídelní stůl"));
        out.println("  Jedním commit() uloženo: zákazník " + order.getCustomerId() + ", adresa "
                + order.getCustomer().getAddresses().stream().map(Address::getCity).toList() + ", objednávka "
                + order.getOrderId() + " ve stavu " + order.getStatus());

        ApplicationContext fresh = ApplicationContext.create(storageType, mapping, dir);
        out.println("  Po novém načtení: zákazníků " + fresh.customerService().findAll().size() + ", objednávek "
                + fresh.orderService().findAll().size());

        try {
            fresh.placeOrderService().placeOrder(new CustomerData.NewCompany("info@svoboda.cz", null, BILLING,
                    "Truhlářství Svoboda s.r.o.", "25596641", "CZ25596641", "Petr Svoboda"),
                    address, "M-NEEXISTUJE", spec(80, 40, 40, null));
        } catch (RuntimeException e) {
            out.println("  Objednávka odmítnuta: " + e.getMessage());
        }
        ApplicationContext after = ApplicationContext.create(storageType, mapping, dir);
        out.println("  Nic z odmítnuté operace se neuložilo: zákazníků " + after.customerService().findAll().size()
                + ", objednávek " + after.orderService().findAll().size());
    }

    private void identityMap(Path dir) {
        ApplicationContext.create(storageType, mapping, dir).customerService().createPersonCustomer("C-123", "jana@example.com", null,
                BILLING, "Jana", "Nováková", LocalDate.of(1990, 5, 14));
        CustomerService customers = ApplicationContext.create(storageType, mapping, dir).customerService();
        Customer first = customers.findCustomer("C-123");
        Customer second = customers.findCustomer("C-123");
        out.println("  Dvě načtení C-123 v jedné Unit of Work vrátí stejnou instanci: " + (first == second));
        customers.addAddress("C-123", "Hlavní 1", "Ostrava", "70800", "CZ");
        out.println("  Po commit() Unit of Work končí, další načtení je nová instance: "
                + (customers.findCustomer("C-123") != first));
    }

    private void lazyLoad(Path dir) {
        ApplicationContext seed = ApplicationContext.create(storageType, mapping, dir);
        seed.materialService().createMaterial("M-OAK", "Dub");
        Order placed = seed.placeOrderService().placeOrder(new CustomerData.NewPerson("jana@example.com", "+420 777 123 456", BILLING, "Jana", "Nováková",
                LocalDate.of(1990, 5, 14)), new AddressData("Hlavní 1", "Ostrava", "70800", "CZ"), "M-OAK",
                spec(120, 75, 60, null));

        ApplicationContext fresh = ApplicationContext.create(storageType, mapping, dir);
        Order order = fresh.orderService().findOrder(placed.getOrderId());
        out.println("  Načtena objednávka " + order.getOrderId() + " ve stavu " + order.getStatus()
                + ", zákazník načten: " + order.isCustomerLoaded());
        Customer customer = order.getCustomer();
        out.println("  Po prvním přístupu: " + customer.getDisplayName() + ", zákazník načten: " + order.isCustomerLoaded()
                + ", adresy načteny: " + customer.isAddressesLoaded());
        out.println("  Adresy po prvním přístupu: " + customer.getAddresses().stream().map(Address::getCity).toList()
                + ", opakovaný přístup vrací stejného zákazníka: " + (order.getCustomer() == customer));
    }

    private static OrderSpecification spec(double width, double height, double depth, String notes) {
        return OrderSpecification.builder().width(width).height(height).depth(depth).designNotes(notes).build();
    }
}
