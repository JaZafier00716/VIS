package cz.vsb.catering.business.e2e;

import static org.junit.jupiter.api.Assertions.*;

import cz.vsb.catering.business.domain.Customer;
import cz.vsb.catering.business.domain.ValidationException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** UC05 Spravovat zákazníky «CRUD»: create, search, update, delete, unique e-mail and the refused
 *  delete of a customer with projects; every step checked with JDBC. */
class Uc05CustomersTest extends E2eBaseTest {
    private final List<Long> created = new ArrayList<>();

    @AfterEach
    void deleteCustomers() {
        created.forEach(id -> exec("DELETE FROM customers WHERE customer_id = ?", id));
    }

    @Test
    void createSearchUpdateDelete() {
        Customer c = customers.create(" Eva Testová ", "Test s.r.o.", "e2e.eva@example.com", "+420 777 111 222", "");
        created.add(c.getId());
        Map<String, Object> row = row("SELECT * FROM customers WHERE customer_id = ?", c.getId());
        assertEquals("Eva Testová", row.get("NAME"));
        assertEquals("Test s.r.o.", row.get("COMPANY_NAME"));
        assertEquals("+420 777 111 222", row.get("PHONE"));
        assertNull(row.get("CITY"));
        assertEquals(List.of(c.getId()), customers.search("e2e.eva").stream().map(Customer::getId).toList());
        assertEquals(count("SELECT COUNT(*) FROM customers"), customers.search(" ").size());

        customers.update(c.getId(), "Eva Nová", null, "e2e.eva2@example.com", null, "Opava");
        row = row("SELECT * FROM customers WHERE customer_id = ?", c.getId());
        assertEquals("Eva Nová", row.get("NAME"));
        assertNull(row.get("COMPANY_NAME"));
        assertEquals("e2e.eva2@example.com", row.get("EMAIL"));
        assertEquals("Opava", row.get("CITY"));

        customers.delete(c.getId());
        assertEquals(0, count("SELECT COUNT(*) FROM customers WHERE customer_id = ?", c.getId()));
    }

    @Test
    void duplicateEmailAndInvalidDataAreRefused() {
        String seedEmail = (String) row("SELECT email FROM customers WHERE customer_id = 1").get("EMAIL");
        ValidationException e = assertThrows(ValidationException.class,
                () -> customers.create("Kopie", null, seedEmail, null, null));
        assertTrue(e.getFieldErrors().containsKey("email"));
        e = assertThrows(ValidationException.class, () -> customers.create("X", null, "x@example.com", "123", null));
        assertTrue(e.getFieldErrors().containsKey("phone"));
        assertEquals(0, count("SELECT COUNT(*) FROM customers WHERE name IN ('Kopie', 'X')"));
    }

    @Test
    void customerWithProjectsIsNotDeleted() {
        long id = count("SELECT MIN(customer_id) FROM projects");
        ValidationException e = assertThrows(ValidationException.class, () -> customers.delete(id));
        assertTrue(e.getMessage().contains("má projekty"), e.getMessage());
        assertEquals(1, count("SELECT COUNT(*) FROM customers WHERE customer_id = ?", id));
    }
}
