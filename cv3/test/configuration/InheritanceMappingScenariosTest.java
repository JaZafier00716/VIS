package configuration;

import business.domainmodel.BillingAddress;
import business.domainmodel.CompanyCustomer;
import business.domainmodel.Customer;
import business.domainmodel.Order;
import business.domainmodel.OrderSpecification;
import business.domainmodel.PersonCustomer;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import support.IsolatedJsonStorage;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The cv07 p.17 test scenarios for every storage × strategy combination (JSON/H2 × CTI/STI). The stored rows are
 * read directly (plain JDBC for H2, the files for JSON), not through the application.
 */
class InheritanceMappingScenariosTest {
    private static final BillingAddress BILLING = new BillingAddress("Hlavní 1", "Ostrava", "70800", "CZ");
    private static final LocalDate BIRTH = LocalDate.of(1990, 5, 14);

    @TempDir
    Path dir;
    private IsolatedJsonStorage json;

    static Stream<Arguments> combinations() {
        return Stream.of(StorageType.values()).flatMap(storage -> Stream.of(CustomerMapping.values())
                .map(mapping -> Arguments.of(storage, mapping)));
    }

    @BeforeEach
    void setUp() {
        json = new IsolatedJsonStorage(dir);
    }

    @AfterEach
    void tearDown() {
        json.close();
    }

    private ApplicationContext context(StorageType storage, CustomerMapping mapping) {
        return ApplicationContext.create(storage, mapping, dir);
    }

    private PersonCustomer createPerson(ApplicationContext context) {
        return context.customerService().createPersonCustomer("C-P", "jana@example.com", "+420 777 123 456",
                BILLING, "Jana", "Nováková", BIRTH);
    }

    private CompanyCustomer createCompany(ApplicationContext context) {
        return context.customerService().createCompanyCustomer("C-F", "info@svoboda.cz", null, null,
                "Truhlářství Svoboda s.r.o.", "12345679", "CZ12345679", "Petr Svoboda");
    }

    // --- cv07 p.17: "Vytvoření fyzické osoby / firmy" ---

    @ParameterizedTest
    @MethodSource("combinations")
    void creatingAPersonAndACompanyGivesTheRightSubclassWithItsData(StorageType storage, CustomerMapping mapping) {
        ApplicationContext context = context(storage, mapping);

        PersonCustomer person = createPerson(context);
        CompanyCustomer company = createCompany(context);

        assertPerson(person);
        assertCompany(company);
    }

    // --- cv07 p.17: "Uložení zákazníka – data jsou ve správných tabulkách podle strategie" ---

    @ParameterizedTest
    @MethodSource("combinations")
    void savedCustomersAreInTheTablesOfTheStrategy(StorageType storage, CustomerMapping mapping) throws Exception {
        ApplicationContext context = context(storage, mapping);
        createPerson(context);
        createCompany(context);

        if (mapping == CustomerMapping.CTI) {
            List<Map<String, Object>> customers = rows(storage, mapping, "customers");
            assertEquals(List.of("C-F", "C-P"), ids(customers));
            assertEquals("P", row(customers, "C-P").get("customer_type"));
            assertEquals("C", row(customers, "C-F").get("customer_type"));
            assertEquals("Ostrava", row(customers, "C-P").get("billing_city"), "common attributes in customers");
            assertFalse(row(customers, "C-P").containsKey("first_name"), "no subtype columns in customers");

            List<Map<String, Object>> persons = rows(storage, mapping, "person_customers");
            List<Map<String, Object>> companies = rows(storage, mapping, "company_customers");
            assertEquals(List.of("C-P"), ids(persons), "the person only in person_customers");
            assertEquals(List.of("C-F"), ids(companies), "the company only in company_customers");
            assertEquals("Nováková", row(persons, "C-P").get("last_name"));
            assertEquals("12345679", row(companies, "C-F").get("ico"));
            assertFalse(exists(storage, mapping, "customers_sti"));
        } else {
            List<Map<String, Object>> all = rows(storage, mapping, "customers_sti");
            assertEquals(List.of("C-F", "C-P"), ids(all), "both types in the single table");
            Map<String, Object> person = row(all, "C-P");
            Map<String, Object> company = row(all, "C-F");
            assertEquals("P", person.get("customer_type"));
            assertEquals("Jana", person.get("first_name"));
            assertNull(person.get("company_name"), "the other subtype's columns are NULL");
            assertNull(person.get("ico"));
            assertEquals("C", company.get("customer_type"));
            assertEquals("Truhlářství Svoboda s.r.o.", company.get("company_name"));
            assertNull(company.get("first_name"));
            assertNull(company.get("billing_city"), "no billing address = NULL columns");
            assertFalse(exists(storage, mapping, "customers"));
            assertFalse(exists(storage, mapping, "person_customers"));
        }
    }

    // --- cv07 p.17: "Načtení osoby / firmy podle ID" ---

    @ParameterizedTest
    @MethodSource("combinations")
    void loadingByIdReturnsTheRightSubclassWithAllOriginalValues(StorageType storage, CustomerMapping mapping) {
        ApplicationContext seed = context(storage, mapping);
        createPerson(seed);
        createCompany(seed);

        ApplicationContext fresh = context(storage, mapping);
        assertPerson(assertInstanceOf(PersonCustomer.class, fresh.customerService().findCustomer("C-P")));
        assertCompany(assertInstanceOf(CompanyCustomer.class, fresh.customerService().findCustomer("C-F")));
        List<Customer> all = context(storage, mapping).customerService().findAll();
        assertEquals(2, all.size());
        assertTrue(all.stream().anyMatch(PersonCustomer.class::isInstance));
        assertTrue(all.stream().anyMatch(CompanyCustomer.class::isInstance));
    }

    // --- cv07 p.17: "Vytvoření objednávky – odkazuje na existujícího zákazníka" ---

    @ParameterizedTest
    @MethodSource("combinations")
    void anOrderReferencesAnExistingCustomer(StorageType storage, CustomerMapping mapping) throws Exception {
        ApplicationContext context = context(storage, mapping);
        createPerson(context);
        createCompany(context);
        context.materialService().createMaterial("M-OAK", "Dub");

        context.orderService().createOrder("O-P", "C-P", "M-OAK", spec());
        context.orderService().createOrder("O-F", "C-F", "M-OAK", spec());

        List<Map<String, Object>> orders = rows(storage, mapping, "orders");
        assertEquals("C-P", orders.stream().filter(o -> "O-P".equals(o.get("order_id"))).findFirst().orElseThrow()
                .get("customer_id"));
        assertEquals("C-F", orders.stream().filter(o -> "O-F".equals(o.get("order_id"))).findFirst().orElseThrow()
                .get("customer_id"));
        assertThrows(RuntimeException.class,
                () -> context(storage, mapping).orderService().createOrder("O-X", "C-404", "M-OAK", spec()));
        assertThrows(RuntimeException.class, () -> context(storage, mapping).customerService()
                .deleteCustomer("C-P"), "orders still block the delete");
    }

    // --- cv07 p.17: "Opětovné načtení objednávky – zákazník má správný typ a původní hodnoty" ---

    @ParameterizedTest
    @MethodSource("combinations")
    void reloadedOrderHasItsCustomerWithTheRightTypeAndValues(StorageType storage, CustomerMapping mapping) {
        ApplicationContext context = context(storage, mapping);
        context.materialService().createMaterial("M-OAK", "Dub");
        createPerson(context);
        createCompany(context);
        context.orderService().createOrder("O-P", "C-P", "M-OAK", spec());
        context.orderService().createOrder("O-F", "C-F", "M-OAK", spec());

        ApplicationContext fresh = context(storage, mapping);
        Order personOrder = fresh.orderService().findOrder("O-P");
        Order companyOrder = fresh.orderService().findOrder("O-F");
        assertPerson(assertInstanceOf(PersonCustomer.class, personOrder.getCustomer()));
        assertCompany(assertInstanceOf(CompanyCustomer.class, companyOrder.getCustomer()));
    }

    @ParameterizedTest
    @MethodSource("combinations")
    void deletingACustomerRemovesAllItsRowsAndAddresses(StorageType storage, CustomerMapping mapping)
            throws Exception {
        ApplicationContext context = context(storage, mapping);
        createPerson(context);
        context.customerService().addAddress("C-P", "Dílenská 5", "Ostrava", "70900", "CZ");

        context(storage, mapping).customerService().deleteCustomer("C-P");

        for (String table : mapping == CustomerMapping.CTI
                ? List.of("customers", "person_customers", "addresses") : List.of("customers_sti", "addresses")) {
            assertTrue(rows(storage, mapping, table).isEmpty(), table);
        }
    }

    private static void assertPerson(PersonCustomer person) {
        assertEquals("C-P", person.getCustomerId());
        assertEquals("jana@example.com", person.getEmail());
        assertEquals("+420 777 123 456", person.getPhone());
        assertEquals(BILLING, person.getBillingAddress());
        assertEquals("Jana", person.getFirstName());
        assertEquals("Nováková", person.getLastName());
        assertEquals(BIRTH, person.getBirthDate());
    }

    private static void assertCompany(CompanyCustomer company) {
        assertEquals("C-F", company.getCustomerId());
        assertEquals("info@svoboda.cz", company.getEmail());
        assertNull(company.getPhone());
        assertNull(company.getBillingAddress());
        assertEquals("Truhlářství Svoboda s.r.o.", company.getCompanyName());
        assertEquals("12345679", company.getIco());
        assertEquals("CZ12345679", company.getDic());
        assertEquals("Petr Svoboda", company.getContactPerson());
    }

    private static OrderSpecification spec() {
        return OrderSpecification.builder().width(120).height(75).depth(60).build();
    }

    // --- reading the stored rows directly ---

    private boolean exists(StorageType storage, CustomerMapping mapping, String table) throws Exception {
        if (storage == StorageType.JSON) {
            return Files.exists(jsonFile(mapping, table));
        }
        try (Connection connection = h2(mapping);
             ResultSet rs = connection.getMetaData().getTables(null, "PUBLIC", table.toUpperCase(Locale.ROOT),
                     new String[]{"TABLE"})) {
            return rs.next();
        }
    }

    /** All rows of a table (H2) or JSON file, with snake_case column names. */
    private List<Map<String, Object>> rows(StorageType storage, CustomerMapping mapping, String table)
            throws Exception {
        List<Map<String, Object>> rows = new ArrayList<>();
        if (storage == StorageType.JSON) {
            Path file = jsonFile(mapping, table);
            if (!Files.exists(file)) {
                return rows;
            }
            List<Map<String, Object>> records = new ObjectMapper().readValue(file.toFile(), new TypeReference<>() {});
            for (Map<String, Object> record : records) {
                Map<String, Object> row = new LinkedHashMap<>();
                record.forEach((key, value) -> row.put(key.replaceAll("([A-Z])", "_$1").toLowerCase(Locale.ROOT),
                        value));
                rows.add(row);
            }
            return rows;
        }
        try (Connection connection = h2(mapping); Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery("SELECT * FROM " + table)) {
            ResultSetMetaData meta = rs.getMetaData();
            while (rs.next()) {
                Map<String, Object> row = new LinkedHashMap<>();
                for (int i = 1; i <= meta.getColumnCount(); i++) {
                    Object value = rs.getObject(i);
                    row.put(meta.getColumnLabel(i).toLowerCase(Locale.ROOT), value == null ? null : value.toString());
                }
                rows.add(row);
            }
        }
        return rows;
    }

    private Path jsonFile(CustomerMapping mapping, String table) {
        return dir.resolve("json-" + mapping.name().toLowerCase(Locale.ROOT)).resolve(table + ".json");
    }

    private Connection h2(CustomerMapping mapping) throws Exception {
        Path file = dir.resolve("vis-h2-" + mapping.name().toLowerCase(Locale.ROOT));
        return DriverManager.getConnection("jdbc:h2:file:" + file.toAbsolutePath(), "sa", "");
    }

    private static List<String> ids(List<Map<String, Object>> rows) {
        return rows.stream().map(r -> String.valueOf(r.get("customer_id"))).sorted().toList();
    }

    private static Map<String, Object> row(List<Map<String, Object>> rows, String customerId) {
        return rows.stream().filter(r -> customerId.equals(r.get("customer_id"))).findFirst().orElseThrow();
    }
}
