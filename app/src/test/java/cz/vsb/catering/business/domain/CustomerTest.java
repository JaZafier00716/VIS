package cz.vsb.catering.business.domain;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Set;
import org.junit.jupiter.api.Test;

class CustomerTest {
    @Test void createTrimsAndBlankOptionalFieldsBecomeNull() {
        Customer c = Customer.create(" Jana Nováková ", " ", " jana@abc.cz ", "+420 603 123 456", "");
        assertEquals("Jana Nováková", c.getName());
        assertNull(c.getCompanyName());
        assertEquals("jana@abc.cz", c.getEmail());
        assertEquals("+420 603 123 456", c.getPhone());
        assertNull(c.getCity());
    }

    @Test void invalidFieldsAreReportedTogether() {
        ValidationException e = assertThrows(ValidationException.class,
                () -> Customer.create("", "F".repeat(101), "bez-zavinace", "12-34", "M".repeat(61)));
        assertEquals(Set.of("name", "companyName", "email", "phone", "city"), e.getFieldErrors().keySet());
    }

    @Test void phoneRule() {
        assertDoesNotThrow(() -> Customer.create("A", null, "a@b.cz", "603123456", null));
        assertThrows(ValidationException.class, () -> Customer.create("A", null, "a@b.cz", "60312", null));
        assertThrows(ValidationException.class, () -> Customer.create("A", null, "a@b.cz", "603 123 456 x", null));
    }

    @Test void refusedUpdateKeepsOldValues() {
        Customer c = Customer.create("A", null, "a@b.cz", null, null);
        assertThrows(ValidationException.class, () -> c.update("B", null, "", null, null));
        assertEquals("A", c.getName());
        assertEquals("a@b.cz", c.getEmail());
    }
}
