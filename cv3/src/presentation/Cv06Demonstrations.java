package presentation;

import business.domainmodel.Address;
import business.domainmodel.BillingAddress;
import business.domainmodel.Customer;
import business.domainmodel.Finish;
import business.domainmodel.Material;
import business.domainmodel.Order;
import business.domainmodel.OrderItem;
import business.domainmodel.OrderSpecification;
import business.domainmodel.ProductConfiguration;
import business.service.CustomerService;
import business.service.MaterialService;
import business.service.OrderService;
import configuration.ApplicationContext;
import configuration.CustomerMapping;
import configuration.StorageType;

import java.io.PrintStream;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * Runs one short scenario per CV06 pattern. Each scenario gets a fresh storage in
 * {@code data/demo/cv06/<n>-<pattern>}, so the stored records can be inspected afterwards.
 * The presentation layer only calls services; it never touches SQL or files directly.
 */
public class Cv06Demonstrations {
    private static final BillingAddress BILLING = new BillingAddress("Hlavní 1", "Ostrava", "70800", "CZ");
    private final PrintStream out;
    private final StorageType storageType;
    private final CustomerMapping mapping;
    private final Path root;

    public Cv06Demonstrations(PrintStream out, StorageType storageType, CustomerMapping mapping, Path root) {
        this.out = out;
        this.storageType = storageType;
        this.mapping = mapping;
        this.root = root;
    }

    public static Cv06Demonstrations standard(PrintStream out, StorageType storageType, CustomerMapping mapping) {
        return new Cv06Demonstrations(out, storageType, mapping, Path.of("data", "demo", "cv06"));
    }

    public void runAll() {
        run("1-identity-field", "Identity Field", this::identityField);
        run("2-foreign-key-mapping", "Foreign Key Mapping", this::foreignKeyMapping);
        run("3-association-table-mapping", "Association Table Mapping", this::associationTableMapping);
        run("4-dependent-mapping", "Dependent Mapping", this::dependentMapping);
        run("5-embedded-value", "Embedded Value", this::embeddedValue);
        run("6-serialized-lob", "Serialized LOB", this::serializedLob);
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

    private void identityField(Path dir) {
        CustomerService customers = ApplicationContext.create(storageType, mapping, dir).customerService();
        Customer generated = customers.createPersonCustomer(null, "jana@example.com", "+420 777 123 456", BILLING,
                "Jana", "Nováková", LocalDate.of(1990, 5, 14));
        Customer explicit = customers.createCompanyCustomer("C-001", "info@svoboda.cz", null, BILLING,
                "Truhlářství Svoboda s.r.o.", "25596641", "CZ25596641", "Petr Svoboda");
        out.println("  Vygenerované ID: " + generated.getCustomerId());
        out.println("  Zadané ID:       " + explicit.getCustomerId());
        out.println("  Dvě načtení C-001 v jedné Unit of Work, stejná instance (Identity Map): "
                + (customers.findCustomer("C-001") == customers.findCustomer("C-001")));

        CustomerService fresh = ApplicationContext.create(storageType, mapping, dir).customerService();
        Customer reloaded = fresh.findCustomer(generated.getCustomerId());
        out.println("  Po novém načtení podle ID: " + reloaded.getDisplayName() + " <" + reloaded.getEmail() + ">");
    }

    private void foreignKeyMapping(Path dir) {
        ApplicationContext context = ApplicationContext.create(storageType, mapping, dir);
        context.customerService().createPersonCustomer("C-001", "jana@example.com", null, BILLING, "Jana",
                "Nováková", LocalDate.of(1990, 5, 14));
        context.materialService().createMaterial("M-OAK", "Dub");
        context.orderService().createOrder("O-001", "C-001", "M-OAK", spec(120, 75, 60, "Jídelní stůl"));
        context.customerService().addAddress("C-001", "Hlavní 1", "Ostrava", "70800", "CZ");

        ApplicationContext fresh = ApplicationContext.create(storageType, mapping, dir);
        Order order = fresh.orderService().findOrder("O-001");
        out.println("  orders.customer_id = " + order.getCustomerId());
        out.println("  Zákazník načtený přes cizí klíč: " + order.getCustomer().getDisplayName());
        out.println("  Adresy zákazníka (customer_id v addresses): "
                + order.getCustomer().getAddresses().stream().map(Address::getCity).toList());
        try {
            fresh.orderService().createOrder("O-BAD", "C-NEEXISTUJE", "M-OAK", spec(1, 1, 1, null));
        } catch (RuntimeException e) {
            out.println("  Objednávka na neexistujícího zákazníka odmítnuta: " + e.getMessage());
        }
    }

    private void associationTableMapping(Path dir) {
        MaterialService materials = ApplicationContext.create(storageType, mapping, dir).materialService();
        materials.createMaterial("M-OAK", "Dub");
        materials.createMaterial("M-BEECH", "Buk");
        materials.createFinish("F-LACQ", "Lak");
        materials.createFinish("F-OIL", "Olej");
        materials.addFinish("M-OAK", "F-LACQ");
        materials.addFinish("M-OAK", "F-OIL");
        materials.addFinish("M-BEECH", "F-LACQ");
        out.println("  Opakované přidání stejné vazby uloženo: " + materials.addFinish("M-OAK", "F-OIL"));

        MaterialService fresh = ApplicationContext.create(storageType, mapping, dir).materialService();
        out.println("  Dub -> " + fresh.findFinishes("M-OAK").stream().map(Finish::getName).toList());
        out.println("  Lak <- " + fresh.findMaterialsWithFinish("F-LACQ").stream().map(Material::getName).toList());
        fresh.removeFinish("M-OAK", "F-OIL");
        out.println("  Po odebrání oleje: Dub -> "
                + ApplicationContext.create(storageType, mapping, dir).materialService().findFinishes("M-OAK").stream()
                .map(Finish::getName).toList());
    }

    private void dependentMapping(Path dir) {
        ApplicationContext context = seeded(dir);
        OrderService orders = context.orderService();
        orders.createOrder("O-001", "C-001", "M-OAK", spec(200, 90, 45, "Komoda"));
        orders.addItem("O-001", "M-OAK", 1, new BigDecimal("12500.00"), null);
        orders.addItem("O-001", "M-OAK", 4, new BigDecimal("850.00"), null);
        orders.changeItemQuantity("O-001", 2, 6);
        orders.addItem("O-001", "M-OAK", 2, new BigDecimal("300.00"), null);
        orders.removeItem("O-001", 1);

        Order reloaded = ApplicationContext.create(storageType, mapping, dir).orderService().findOrder("O-001");
        out.println("  Položky po změnách: " + reloaded.getItems().stream()
                .map(item -> "#" + item.getLineNumber() + " x" + item.getQuantity()).toList());
        ApplicationContext.create(storageType, mapping, dir).orderService().deleteOrder("O-001");
        out.println("  Objednávka smazána i s položkami.");
    }

    private void embeddedValue(Path dir) {
        ApplicationContext context = seeded(dir);
        OrderSpecification specification = spec(180.5, 76, 90, "Pracovní stůl, kabelová průchodka");
        context.orderService().createOrder("O-001", "C-001", "M-OAK", specification);
        Order reloaded = ApplicationContext.create(storageType, mapping, dir).orderService().findOrder("O-001");
        out.println("  Uloženo jako sloupce width/height/depth/design_notes objednávky.");
        out.println("  Načtená specifikace: " + reloaded.getSpecification());
        out.println("  Rovná se původní hodnotě: " + reloaded.getSpecification().equals(specification));
        try {
            spec(0, 10, 10, null);
        } catch (IllegalArgumentException e) {
            out.println("  Neplatná hodnota odmítnuta: " + e.getMessage());
        }
    }

    private void serializedLob(Path dir) {
        ApplicationContext context = seeded(dir);
        context.orderService().createOrder("O-001", "C-001", "M-OAK", spec(90, 200, 60, "Šatní skříň"));
        ProductConfiguration configuration = new ProductConfiguration("bílá", "CNC frézování",
                Map.of("úchytky", "černé kovové", "zámek", "ano", "police", "4"));
        context.orderService().addItem("O-001", "M-OAK", 1, new BigDecimal("18900.00"), configuration);

        OrderItem item = ApplicationContext.create(storageType, mapping, dir).orderService().findOrder("O-001")
                .getItems().get(0);
        out.println("  Konfigurace uložená jako jeden JSON text (sloupec configuration_json):");
        out.println("    barva=" + item.getConfiguration().getColor() + ", opracování="
                + item.getConfiguration().getMachining() + ", volby=" + item.getConfiguration().getOptions()
                .entrySet().stream().map(e -> e.getKey() + ":" + e.getValue()).collect(Collectors.joining(", ")));
        out.println("  Rovná se původní konfiguraci: " + item.getConfiguration().equals(configuration));
    }

    private ApplicationContext seeded(Path dir) {
        ApplicationContext context = ApplicationContext.create(storageType, mapping, dir);
        context.customerService().createPersonCustomer("C-001", "jana@example.com", null, BILLING, "Jana",
                "Nováková", LocalDate.of(1990, 5, 14));
        context.materialService().createMaterial("M-OAK", "Dub");
        return context;
    }

    private static OrderSpecification spec(double width, double height, double depth, String notes) {
        return OrderSpecification.builder().width(width).height(height).depth(depth).designNotes(notes).build();
    }
}
