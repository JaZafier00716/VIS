package business.domainmodel;

import configuration.ApplicationContext;
import configuration.StorageType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import support.IsolatedJsonStorage;

import java.nio.file.Path;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** CV07 task 1 (cv07 p.3, p.16): the Customer hierarchy, its validation and its use by orders. */
class CustomerHierarchyTest {
    private static final BillingAddress BILLING = new BillingAddress("Hlavní 1", "Ostrava", "70800", "CZ");
    private static final LocalDate BIRTH = LocalDate.of(1990, 5, 14);

    @TempDir
    Path dir;

    private static PersonCustomer person(String email, String phone, String first, String last, LocalDate birth) {
        return new PersonCustomer("C-1", email, phone, BILLING, first, last, birth);
    }

    private static CompanyCustomer company(String name, String ico, String dic) {
        return new CompanyCustomer("C-2", "info@firma.cz", null, BILLING, name, ico, dic, "Petr Svoboda");
    }

    @Test
    void validPersonAndCompanyPassAndHaveTheirDisplayNames() {
        PersonCustomer jana = person("jana@example.com", "+420 777 123 456", "Jana", "Nováková", BIRTH);
        CompanyCustomer firma = company("Truhlářství Svoboda s.r.o.", "12345679", "CZ12345679");

        assertDoesNotThrow(jana::validate);
        assertDoesNotThrow(firma::validate);
        assertEquals("Jana Nováková", jana.getDisplayName());
        assertEquals("Truhlářství Svoboda s.r.o.", firma.getDisplayName());
    }

    /**
     * IČOs computed by the rule (weights 8..2 on 1234566x, r = sum % 11, check = (11 − r) % 10), not real
     * company numbers: 1234567 → sum 112, r = 2, check 9; 1234566 → r = 0, check 1; 1234561 → r = 1, check 0;
     * 1234560 → r = 10, check 1.
     */
    @ParameterizedTest
    @ValueSource(strings = {"12345679", "12345661", "12345610", "12345601"})
    void icoWithTheComputedCheckDigitIsValid(String ico) {
        assertTrue(CompanyCustomer.isValidIco(ico));
        assertDoesNotThrow(() -> company("Firma", ico, null).validate());
    }

    @ParameterizedTest
    @ValueSource(strings = {"12345678", "12345660", "12345611", "1234567", "123456789", "1234567A", ""})
    void icoWithAWrongCheckDigitOrFormatIsRejected(String ico) {
        assertFalse(CompanyCustomer.isValidIco(ico));
        assertThrows(IllegalArgumentException.class, () -> company("Firma", ico, null).validate());
    }

    @Test
    void icoIsRequired() {
        assertThrows(IllegalArgumentException.class, () -> company("Firma", null, null).validate());
    }

    @ParameterizedTest
    @ValueSource(strings = {"CZ123", "SK12345679", "CZ12345678901", "cz12345679"})
    void invalidDicIsRejected(String dic) {
        assertThrows(IllegalArgumentException.class, () -> company("Firma", "12345679", dic).validate());
    }

    @Test
    void dicIsOptionalAndAcceptsEightToTenDigits() {
        assertDoesNotThrow(() -> company("Firma", "12345679", null).validate());
        assertDoesNotThrow(() -> company("Firma", "12345679", "CZ12345678").validate());
        assertDoesNotThrow(() -> company("Firma", "12345679", "CZ1234567890").validate());
    }

    @Test
    void birthDateMustBeInThePast() {
        assertThrows(IllegalArgumentException.class,
                () -> person("jana@example.com", null, "Jana", "Nováková", LocalDate.now()).validate(), "today");
        assertDoesNotThrow(
                () -> person("jana@example.com", null, "Jana", "Nováková", LocalDate.now().minusDays(1)).validate());
        assertThrows(IllegalArgumentException.class,
                () -> person("jana@example.com", null, "Jana", "Nováková", LocalDate.now().plusDays(1)).validate());
        assertThrows(IllegalArgumentException.class,
                () -> person("jana@example.com", null, "Jana", "Nováková", null).validate());
    }

    @Test
    void requiredFieldsPerSubtype() {
        assertThrows(IllegalArgumentException.class, () -> person("jana@example.com", null, "", "Nováková", BIRTH)
                .validate());
        assertThrows(IllegalArgumentException.class, () -> person("jana@example.com", null, "Jana", null, BIRTH)
                .validate());
        assertThrows(IllegalArgumentException.class, () -> company(" ", "12345679", null).validate());
    }

    @Test
    void billingAddressIsOptional() {
        assertDoesNotThrow(
                () -> new PersonCustomer("C-1", "jana@example.com", null, null, "Jana", "Nováková", BIRTH).validate());
    }

    @Test
    void commonRulesEmailAndPhone() {
        assertThrows(IllegalArgumentException.class, () -> person("bez-zavinace", null, "Jana", "N", BIRTH)
                .validate());
        assertThrows(IllegalArgumentException.class, () -> person("jana@example.com", "abc", "Jana", "N", BIRTH)
                .validate());
        assertThrows(IllegalArgumentException.class, () -> person("jana@example.com", "123 45", "Jana", "N", BIRTH)
                .validate());
        assertDoesNotThrow(() -> person("jana@example.com", "777123456", "Jana", "N", BIRTH).validate());
        assertDoesNotThrow(() -> person("jana@example.com", null, "Jana", "N", BIRTH).validate());
    }

    @Test
    void billingAddressIsAValueObject() {
        assertEquals(new BillingAddress("Hlavní 1", "Ostrava", "70800", "CZ"), BILLING);
        assertNotEquals(new BillingAddress("Hlavní 2", "Ostrava", "70800", "CZ"), BILLING);
        assertThrows(IllegalArgumentException.class, () -> new BillingAddress("Hlavní 1", "", "70800", "CZ"));
    }

    /** cv07 p.3: an order can belong to either type, and the reloaded order returns the right subclass. */
    @ParameterizedTest
    @EnumSource(StorageType.class)
    void ordersOfBothTypesReloadTheirCustomerAsTheRightSubclass(StorageType type) {
        try (IsolatedJsonStorage ignored = new IsolatedJsonStorage(dir)) {
            ApplicationContext seed = ApplicationContext.create(type, dir);
            seed.materialService().createMaterial("M-OAK", "Dub");
            seed.customerService().createPersonCustomer("C-P", "jana@example.com", "+420 777 123 456", BILLING,
                    "Jana", "Nováková", BIRTH);
            seed.customerService().createCompanyCustomer("C-F", "info@svoboda.cz", null, BILLING,
                    "Truhlářství Svoboda s.r.o.", "12345679", "CZ12345679", "Petr Svoboda");
            OrderSpecification spec = OrderSpecification.builder().width(120).height(75).depth(60).build();
            seed.orderService().createOrder("O-P", "C-P", "M-OAK", spec);
            seed.orderService().createOrder("O-F", "C-F", "M-OAK", spec);

            ApplicationContext fresh = ApplicationContext.create(type, dir);
            PersonCustomer person = assertInstanceOf(PersonCustomer.class,
                    fresh.orderService().findOrder("O-P").getCustomer());
            assertEquals("jana@example.com", person.getEmail());
            assertEquals("+420 777 123 456", person.getPhone());
            assertEquals(BILLING, person.getBillingAddress());
            assertEquals("Jana", person.getFirstName());
            assertEquals("Nováková", person.getLastName());
            assertEquals(BIRTH, person.getBirthDate());

            CompanyCustomer company = assertInstanceOf(CompanyCustomer.class,
                    fresh.orderService().findOrder("O-F").getCustomer());
            assertEquals("info@svoboda.cz", company.getEmail());
            assertEquals(null, company.getPhone());
            assertEquals(BILLING, company.getBillingAddress());
            assertEquals("Truhlářství Svoboda s.r.o.", company.getCompanyName());
            assertEquals("12345679", company.getIco());
            assertEquals("CZ12345679", company.getDic());
            assertEquals("Petr Svoboda", company.getContactPerson());
        }
    }

    /** No billing address = four NULL columns, and four NULL columns load back as null. */
    @ParameterizedTest
    @EnumSource(StorageType.class)
    void missingBillingAddressRoundTripsAsNull(StorageType type) {
        try (IsolatedJsonStorage ignored = new IsolatedJsonStorage(dir)) {
            ApplicationContext.create(type, dir).customerService().createCompanyCustomer("C-F", "info@firma.cz",
                    null, null, "Firma", "12345679", null, null);

            Customer loaded = ApplicationContext.create(type, dir).customerService().findCustomer("C-F");
            assertEquals(null, loaded.getBillingAddress());
        }
    }

    @Test
    void invalidCustomerIsRejectedByTheServiceAndNotStored() {
        try (IsolatedJsonStorage ignored = new IsolatedJsonStorage(dir)) {
            ApplicationContext context = ApplicationContext.create(StorageType.JSON, dir);
            assertThrows(IllegalArgumentException.class, () -> context.customerService().createCompanyCustomer(null,
                    "info@firma.cz", null, BILLING, "Firma", "12345678", null, null));
            assertTrue(context.customerService().findAll().isEmpty());
        }
    }
}
