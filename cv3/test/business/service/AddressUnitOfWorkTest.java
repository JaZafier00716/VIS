package business.service;

import business.domainmodel.Address;
import business.domainmodel.Customer;
import configuration.ApplicationContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import support.IsolatedJsonStorage;
import support.TestCustomers;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Addresses are committed through the Unit of Work and loaded lazily through their customer. */
class AddressUnitOfWorkTest {
    @TempDir
    Path dir;
    private IsolatedJsonStorage storage;

    @BeforeEach
    void setUp() {
        storage = new IsolatedJsonStorage(dir);
        TestCustomers.person(storage.newContext().customerService(), "C-001", "Jana", "jana@example.com");
    }

    @AfterEach
    void tearDown() {
        storage.close();
    }

    @Test
    void addedAddressIsCommittedAndLoadedAgain() {
        ApplicationContext context = storage.newContext();
        Address address = context.customerService().addAddress("C-001", "Hlavní 1", "Ostrava", "70800", "CZ");

        assertFalse(context.unitOfWork().hasChanges());
        Customer reloaded = storage.newContext().customerService().findCustomer("C-001");
        assertEquals(List.of(address.getAddressId()),
                reloaded.getAddresses().stream().map(Address::getAddressId).toList());
    }

    @Test
    void invalidAddressIsNotStored() {
        CustomerService customers = storage.newContext().customerService();

        assertThrows(IllegalArgumentException.class, () -> customers.addAddress("C-001", "", "", null, null));
        assertTrue(storage.newContext().customerService().findCustomer("C-001").getAddresses().isEmpty());
    }

    @Test
    void deletingTheCustomerRemovesTheirAddresses() {
        storage.newContext().customerService().addAddress("C-001", "Hlavní 1", "Ostrava", "70800", "CZ");

        storage.newContext().customerService().deleteCustomer("C-001");

        assertThrows(IllegalArgumentException.class,
                () -> storage.newContext().customerService().findCustomer("C-001"));
        assertTrue(new dataaccess.persistence.JsonAddressGateway().findAll().isEmpty());
    }
}
