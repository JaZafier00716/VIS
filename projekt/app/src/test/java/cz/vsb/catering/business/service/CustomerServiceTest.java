package cz.vsb.catering.business.service;

import static org.junit.jupiter.api.Assertions.*;

import cz.vsb.catering.business.domain.Customer;
import cz.vsb.catering.business.domain.ValidationException;
import cz.vsb.catering.business.fake.DirectTransactionRunner;
import cz.vsb.catering.business.fake.FakeCustomerMapper;
import org.junit.jupiter.api.Test;

class CustomerServiceTest {
    private final FakeCustomerMapper mapper = new FakeCustomerMapper();
    private final CustomerService service = new CustomerService(mapper, new DirectTransactionRunner());

    { mapper.add(1, "Jana Nováková", "jana@abc.cz"); mapper.add(2, "Petr Dvořák", "petr@xyz.cz"); }

    @Test void createAndSearch() {
        Customer c = service.create("Eva Malá", "Malá s.r.o.", "eva@mala.cz", null, "Opava");
        assertNotNull(c.getId());
        assertEquals("Malá s.r.o.", mapper.stored(c.getId()).getCompanyName());
        assertEquals(1, service.search("opava").size());
        assertEquals(1, service.search(" MALÁ ").size());
        assertEquals(3, service.search("").size());
        assertEquals(3, service.search(".cz").size());
    }

    @Test void duplicateEmailIsFieldError() {
        ValidationException e = assertThrows(ValidationException.class,
                () -> service.create("X", null, "JANA@abc.cz", null, null));
        assertTrue(e.getFieldErrors().containsKey("email"));
        assertEquals(2, mapper.customers.size());
        e = assertThrows(ValidationException.class, () -> service.update(2, "Petr", null, "jana@abc.cz", null, null));
        assertTrue(e.getFieldErrors().containsKey("email"));
        assertEquals("petr@xyz.cz", mapper.stored(2).getEmail());
    }

    @Test void updateKeepsOwnEmail() {
        service.update(1, "Jana Nová", null, "jana@abc.cz", "603123456", "Ostrava");
        assertEquals("Jana Nová", mapper.stored(1).getName());
        assertEquals("603123456", mapper.stored(1).getPhone());
    }

    @Test void invalidUpdateIsNotStored() {
        assertThrows(ValidationException.class, () -> service.update(1, "", null, "jana@abc.cz", null, null));
        assertEquals("Jana Nováková", mapper.stored(1).getName());
        assertThrows(ValidationException.class, () -> service.update(99, "A", null, "a@b.cz", null, null));
    }

    @Test void deleteRefusedWhenCustomerHasProjects() {
        mapper.projectCounts.put(1L, 2);
        ValidationException e = assertThrows(ValidationException.class, () -> service.delete(1));
        assertTrue(e.getMessage().contains("má projekty"), e.getMessage());
        assertNotNull(mapper.stored(1));
        service.delete(2);
        assertNull(mapper.stored(2));
    }
}
