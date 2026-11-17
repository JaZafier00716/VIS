package presentation;

import business.command.CommandLogEntry;
import business.command.ConfirmOrderCommand;
import business.command.IOrderCommand;
import business.command.OrderCommandExecutor;
import business.command.PrepareExpeditionCommand;
import business.command.StartProductionCommand;
import business.document.ConfirmationCreator;
import business.document.DeliveryNoteCreator;
import business.document.DocumentCreator;
import business.document.InvoiceCreator;
import business.document.OrderDocument;
import business.domainmodel.Address;
import business.domainmodel.BillingAddress;
import business.domainmodel.CompanyCustomer;
import business.domainmodel.Customer;
import business.domainmodel.DiscountPolicy;
import business.domainmodel.Finish;
import business.domainmodel.Material;
import business.domainmodel.NoDiscount;
import business.domainmodel.Order;
import business.domainmodel.OrderItem;
import business.domainmodel.OrderSpecification;
import business.domainmodel.PercentageDiscount;
import business.domainmodel.ProductConfiguration;
import business.observer.ExpeditionObserver;
import business.observer.ProductionObserver;
import business.pricing.CompanyPricing;
import business.pricing.IPricingStrategy;
import business.pricing.StandardPricing;
import business.service.PlaceOrderService;
import configuration.ApplicationContext;
import configuration.CustomerMapping;
import configuration.ShippingMode;
import configuration.StorageType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Scanner;

/**
 * Console UI. Usage:
 * <pre>
 *   mvn compile exec:java                                   # interactive, JSON storage
 *   mvn compile exec:java -Dexec.args="--storage=h2"        # interactive, H2 storage
 *   mvn compile exec:java -Dexec.args="--storage=h2 --demo" # CV05–CV09 demonstrations
 *   mvn compile exec:java -Dexec.args="--mapping=sti"       # CV07: Single Table Inheritance (default cti)
 *   mvn compile exec:java -Dexec.args="--shipping=stub"     # CV08: shipping stub (default gateway)
 * </pre>
 */
public class Main {
    private final ApplicationContext context;
    private final Scanner in;
    private final ShippingMode shippingMode;
    /** CV09 Command: one invoker for the whole session, so its log is the history of this run. */
    private final OrderCommandExecutor executor = new OrderCommandExecutor();

    Main(ApplicationContext context, Scanner in, ShippingMode shippingMode) {
        this.context = context;
        this.in = in;
        this.shippingMode = shippingMode;
        // CV09 Observer: production and expedition write their messages to the console.
        context.orderService().registerObserver(new ProductionObserver(System.out::println));
        context.orderService().registerObserver(new ExpeditionObserver(System.out::println));
    }

    public static void main(String[] args) {
        StorageType storage = StorageType.fromSystemProperty();
        CustomerMapping mapping = CustomerMapping.fromSystemProperty();
        ShippingMode shipping = ShippingMode.fromSystemProperty();
        boolean demo = false;
        for (String arg : args) {
            if (arg.startsWith("--storage=")) {
                storage = StorageType.parse(arg.substring("--storage=".length()));
            } else if (arg.startsWith("--mapping=")) {
                mapping = CustomerMapping.parse(arg.substring("--mapping=".length()));
            } else if (arg.startsWith("--shipping=")) {
                shipping = ShippingMode.parse(arg.substring("--shipping=".length()));
            } else if (arg.equals("--demo")) {
                demo = true;
            }
        }
        if (demo) {
            Cv05Demonstrations.standard(System.out, storage, mapping).runAll();
            Cv06Demonstrations.standard(System.out, storage, mapping).runAll();
            Cv07Demonstrations.standard(System.out, storage).runAll();
            Cv08Demonstrations.standard(System.out, storage, mapping).runAll();
            Cv09Demonstrations.standard(System.out, storage, mapping).runAll();
            return;
        }
        new Main(ApplicationContext.create(storage, mapping, ApplicationContext.defaultDataDirectory()),
                new Scanner(System.in), shipping).loop();
    }

    void loop() {
        System.out.println("Zakázková výroba nábytku - úložiště " + context.storageType() + ", mapování zákazníků "
                + context.customerMapping() + ", doprava " + shippingMode);
        while (true) {
            System.out.println("""

                    1  Vypsat zákazníky            2  Přidat zákazníka        3  Přidat adresu
                    4  Vypsat materiály            5  Přidat materiál         6  Přidat povrchovou úpravu
                    7  Přiřadit úpravu materiálu   8  Vypsat objednávky       9  Vytvořit objednávku
                    10 Přidat položku              11 Potvrdit objednávku     12 Ukázky CV05–CV09
                    13 Nová objednávka (zákazník + adresa + objednávka)
                    14 Cena objednávky (sleva + doprava)
                    15 Zahájit výrobu              16 Připravit k expedici
                    17 Cena podle cenové strategie 18 Dokument objednávky     19 Historie příkazů
                    0  Konec""");
            String choice = ask("Volba");
            try {
                switch (choice) {
                    case "1" -> listCustomers();
                    case "2" -> addCustomer();
                    case "3" -> addAddress();
                    case "4" -> listMaterials();
                    case "5" -> context.materialService().createMaterial(blankToNull(ask("ID (prázdné = generovat)")),
                            ask("Název"));
                    case "6" -> context.materialService().createFinish(blankToNull(ask("ID (prázdné = generovat)")),
                            ask("Název"));
                    case "7" -> System.out.println(context.materialService()
                            .addFinish(ask("ID materiálu"), ask("ID úpravy")) ? "Přiřazeno." : "Vazba už existuje.");
                    case "8" -> listOrders();
                    case "9" -> createOrder();
                    case "10" -> addItem();
                    case "11" -> execute(new ConfirmOrderCommand(context.orderService(), ask("ID objednávky")));
                    case "12" -> {
                        StorageType storage = context.storageType();
                        Cv05Demonstrations.standard(System.out, storage, context.customerMapping()).runAll();
                        Cv06Demonstrations.standard(System.out, storage, context.customerMapping()).runAll();
                        Cv07Demonstrations.standard(System.out, storage).runAll();
                        Cv08Demonstrations.standard(System.out, storage, context.customerMapping()).runAll();
                        Cv09Demonstrations.standard(System.out, storage, context.customerMapping()).runAll();
                    }
                    case "13" -> placeOrder();
                    case "14" -> priceOrder();
                    case "15" -> execute(new StartProductionCommand(context.orderService(), ask("ID objednávky")));
                    case "16" -> execute(new PrepareExpeditionCommand(context.orderService(), ask("ID objednávky")));
                    case "17" -> priceByStrategy();
                    case "18" -> printDocument();
                    case "19" -> printCommandHistory();
                    case "0" -> {
                        return;
                    }
                    default -> System.out.println("Neznámá volba.");
                }
            } catch (RuntimeException e) {
                System.out.println("Chyba: " + e.getMessage());
            }
        }
    }

    private void listCustomers() {
        for (Customer customer : context.customerService().findAll()) {
            String type = customer instanceof CompanyCustomer ? "firma" : "osoba";
            System.out.println(customer.getCustomerId() + "  [" + type + "] " + customer.getDisplayName() + " <"
                    + customer.getEmail() + ">");
            for (Address address : customer.getAddresses()) {
                System.out.println("    " + address.getStreet() + ", " + address.getZipCode() + " " + address.getCity()
                        + ", " + address.getCountry());
            }
        }
    }

    /** CV07 (cv07 p.15, scenario A step 1–2): the user first chooses person or company, then the fields. */
    private void addCustomer() {
        String id = blankToNull(ask("ID (prázdné = generovat)"));
        Customer customer = switch (readNewCustomer()) {
            case PlaceOrderService.CustomerData.NewPerson p -> context.customerService().createPersonCustomer(id,
                    p.email(), p.phone(), p.billingAddress(), p.firstName(), p.lastName(), p.birthDate());
            case PlaceOrderService.CustomerData.NewCompany c -> context.customerService().createCompanyCustomer(id,
                    c.email(), c.phone(), c.billingAddress(), c.companyName(), c.ico(), c.dic(), c.contactPerson());
            case PlaceOrderService.CustomerData.Existing e -> throw new IllegalStateException();
        };
        System.out.println("Vytvořen zákazník " + customer.getCustomerId() + " (" + customer.getDisplayName() + ")");
    }

    /** Asks for the customer type and the matching fields; the input is validated by the business layer. */
    private PlaceOrderService.CustomerData readNewCustomer() {
        String type = ask("Typ zákazníka: o = fyzická osoba, f = firma").toLowerCase();
        if (!type.equals("o") && !type.equals("f")) {
            throw new IllegalArgumentException("Zvolte o (osoba) nebo f (firma).");
        }
        String email = ask("E-mail");
        String phone = blankToNull(ask("Telefon (nepovinný)"));
        BillingAddress billing = new BillingAddress(ask("Fakturační ulice"), ask("Fakturační město"),
                ask("Fakturační PSČ"), ask("Fakturační stát"));
        if (type.equals("o")) {
            return new PlaceOrderService.CustomerData.NewPerson(email, phone, billing, ask("Jméno"), ask("Příjmení"),
                    LocalDate.parse(ask("Datum narození (RRRR-MM-DD)")));
        }
        return new PlaceOrderService.CustomerData.NewCompany(email, phone, billing, ask("Název firmy"), ask("IČO"),
                blankToNull(ask("DIČ (nepovinné)")), blankToNull(ask("Kontaktní osoba (nepovinná)")));
    }

    private void addAddress() {
        context.customerService().addAddress(ask("ID zákazníka"), ask("Ulice"), ask("Město"), ask("PSČ"), ask("Stát"));
    }

    private void listMaterials() {
        for (Material material : context.materialService().findAllMaterials()) {
            System.out.println(material.getMaterialId() + "  " + material.getName() + "  "
                    + material.getFinishes().stream().map(Finish::getName).toList());
        }
    }

    private void listOrders() {
        for (Order order : context.orderService().findAll()) {
            OrderSpecification spec = order.getSpecification();
            String number = order.getNumber() == null ? "" : "  č. " + order.getNumber();
            System.out.println(order.getOrderId() + number + "  " + order.getStatus().getLabel() + "  zákazník "
                    + order.getCustomer().getDisplayName() + "  " + spec.getWidth() + "x" + spec.getHeight() + "x"
                    + spec.getDepth() + " cm");
            for (OrderItem item : order.getItems()) {
                System.out.println("    #" + item.getLineNumber() + " " + item.getMaterialId() + " x"
                        + item.getQuantity() + " á " + item.getUnitPrice()
                        + (item.getConfiguration() == null ? "" : "  " + item.getConfiguration()));
            }
        }
    }

    /** CV08 (cv08 p.17–18): items → discount → shipping → total, printed as a recapitulation. */
    private void priceOrder() {
        String orderId = ask("ID objednávky");
        String percent = ask("Sleva v % (prázdné = bez slevy)");
        DiscountPolicy discount = percent.isBlank() ? NoDiscount.INSTANCE
                : new PercentageDiscount(Integer.parseInt(percent));
        Cv08Demonstrations.printSummary(System.out,
                context.orderPricingService(shippingMode).calculate(orderId, discount));
    }

    /** CV09 Command: every status operation goes through the one executor, which logs the result. */
    private void execute(IOrderCommand command) {
        System.out.println(Cv09Demonstrations.describe(executor.execute(command)));
    }

    private void printCommandHistory() {
        if (executor.getLog().isEmpty()) {
            System.out.println("Zatím nebyl proveden žádný příkaz.");
        }
        for (CommandLogEntry entry : executor.getLog()) {
            System.out.println(Cv09Demonstrations.describe(entry));
        }
    }

    /** CV09 Strategy: the UI only picks the strategy object; the service never asks which one it got. */
    private void priceByStrategy() {
        String orderId = ask("ID objednávky");
        IPricingStrategy strategy = switch (ask("Strategie: s = standardní, f = firemní").toLowerCase()) {
            case "s" -> new StandardPricing();
            case "f" -> new CompanyPricing();
            default -> throw new IllegalArgumentException("Zvolte s (standardní) nebo f (firemní).");
        };
        System.out.println(strategy.getName() + ": "
                + context.orderPricingService(shippingMode).calculatePrice(orderId, strategy));
    }

    /** CV09 Factory Method: the UI only picks the creator; the creator decides which document is made. */
    private void printDocument() {
        String orderId = ask("ID objednávky");
        DocumentCreator creator = switch (ask("Dokument: p = potvrzení, d = dodací list, f = faktura").toLowerCase()) {
            case "p" -> new ConfirmationCreator();
            case "d" -> new DeliveryNoteCreator();
            case "f" -> new InvoiceCreator();
            default -> throw new IllegalArgumentException("Zvolte p (potvrzení), d (dodací list) nebo f (faktura).");
        };
        OrderDocument document = context.orderDocumentService().createDocument(orderId, creator);
        System.out.println(document.getTitle());
        System.out.println("=".repeat(document.getTitle().length()));
        System.out.print(document.getContent());
    }

    private void createOrder() {
        OrderSpecification spec = OrderSpecification.builder()
                .width(Double.parseDouble(ask("Šířka (cm)")))
                .height(Double.parseDouble(ask("Výška (cm)")))
                .depth(Double.parseDouble(ask("Hloubka (cm)")))
                .designNotes(blankToNull(ask("Poznámka k návrhu"))).build();
        Order order = context.orderService().createOrder(blankToNull(ask("ID (prázdné = generovat)")),
                ask("ID zákazníka"), ask("ID materiálu"), spec);
        System.out.println("Vytvořena objednávka č. " + order.getNumber() + " (ID " + order.getOrderId() + ")");
    }

    /** CV05 scenario A: customer (existing or new), address and order saved by one commit. */
    private void placeOrder() {
        String customerId = blankToNull(ask("ID existujícího zákazníka (prázdné = nový zákazník)"));
        PlaceOrderService.CustomerData customer = customerId != null
                ? PlaceOrderService.CustomerData.existing(customerId)
                : readNewCustomer();
        PlaceOrderService.AddressData address = new PlaceOrderService.AddressData(ask("Ulice"), ask("Město"),
                ask("PSČ"), ask("Stát"));
        String materialId = ask("ID materiálu");
        OrderSpecification spec = OrderSpecification.builder()
                .width(Double.parseDouble(ask("Šířka (cm)")))
                .height(Double.parseDouble(ask("Výška (cm)")))
                .depth(Double.parseDouble(ask("Hloubka (cm)")))
                .designNotes(blankToNull(ask("Poznámka k návrhu"))).build();
        Order order = context.placeOrderService().placeOrder(customer, address, materialId, spec);
        System.out.println("Vytvořena objednávka č. " + order.getNumber() + " (ID " + order.getOrderId()
                + ") zákazníka " + order.getCustomerId() + " ve stavu " + order.getStatus().getLabel());
    }

    private void addItem() {
        String orderId = ask("ID objednávky");
        String materialId = ask("ID materiálu");
        int quantity = Integer.parseInt(ask("Počet kusů"));
        BigDecimal price = new BigDecimal(ask("Jednotková cena"));
        ProductConfiguration configuration = null;
        String color = ask("Barva (prázdné = bez konfigurace)");
        if (!color.isBlank()) {
            String machining = ask("Opracování");
            Map<String, String> options = new LinkedHashMap<>();
            while (true) {
                String key = ask("Volba - název (prázdné = konec)");
                if (key.isBlank()) break;
                options.put(key, ask("Volba - hodnota"));
            }
            configuration = new ProductConfiguration(color, machining, options);
        }
        context.orderService().addItem(orderId, materialId, quantity, price, configuration);
    }

    private String ask(String prompt) {
        System.out.print(prompt + ": ");
        return in.hasNextLine() ? in.nextLine().trim() : "0";
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
