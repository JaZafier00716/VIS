package support;

import business.domainmodel.BillingAddress;
import business.domainmodel.PersonCustomer;
import business.service.CustomerService;
import business.service.PlaceOrderService.CustomerData;

import java.time.LocalDate;

/** Valid test customers. A person named "Jana" gets the display name "Jana Test". */
public final class TestCustomers {
    public static final BillingAddress BILLING = new BillingAddress("Fakturační 1", "Ostrava", "70800", "CZ");
    public static final String LAST_NAME = "Test";
    public static final LocalDate BIRTH_DATE = LocalDate.of(1990, 5, 14);

    private TestCustomers() {}

    public static PersonCustomer person(String id, String firstName, String email) {
        return new PersonCustomer(id, email, null, BILLING, firstName, LAST_NAME, BIRTH_DATE);
    }

    public static PersonCustomer person(CustomerService customers, String id, String firstName, String email) {
        return customers.createPersonCustomer(id, email, null, BILLING, firstName, LAST_NAME, BIRTH_DATE);
    }

    public static CustomerData newPerson(String firstName, String email) {
        return new CustomerData.NewPerson(email, null, BILLING, firstName, LAST_NAME, BIRTH_DATE);
    }
}
