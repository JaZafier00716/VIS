package business.service;

import business.domainmodel.Customer;
import business.domainmodel.Material;
import configuration.ApplicationContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import support.IsolatedJsonStorage;
import support.TestCustomers;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** CV06 - Identity Field: every entity keeps its database key as an ordinary field. */
class IdentityFieldTest {
    @TempDir
    Path dir;
    private IsolatedJsonStorage storage;

    @BeforeEach
    void setUp() {
        storage = new IsolatedJsonStorage(dir);
    }

    @AfterEach
    void tearDown() {
        storage.close();
    }

    @Test
    void generatesAUniqueIdBeforeSaving() {
        CustomerService customers = storage.newContext().customerService();
        Customer first = TestCustomers.person(customers, null, "Jana", "jana@example.com");
        Customer second = TestCustomers.person(customers, null, "Petr", "petr@example.com");

        assertTrue(first.getCustomerId().startsWith("C-"));
        assertNotEquals(first.getCustomerId(), second.getCustomerId());
    }

    @Test
    void keepsAnExplicitIdAndFindsTheRecordByIt() {
        TestCustomers.person(storage.newContext().customerService(), "C-001", "Jana", "jana@example.com");

        Customer reloaded = storage.newContext().customerService().findCustomer("C-001");

        assertEquals("C-001", reloaded.getCustomerId());
        assertEquals("Jana Test", reloaded.getDisplayName());
    }

    @Test
    void sameIdGivesTheSameInstanceWithinOneUnitOfWork() {
        ApplicationContext context = storage.newContext();
        Material created = context.materialService().createMaterial("M-OAK", "Dub");
        Material first = context.materialService().findMaterial("M-OAK");

        assertSame(first, context.materialService().findMaterial("M-OAK"));
        assertNotSame(created, first, "the commit ended the unit of work that held the created instance");
        assertNotSame(first, storage.newContext().materialService().findMaterial("M-OAK"));
    }

    @Test
    void duplicateIdIsRejected() {
        CustomerService customers = storage.newContext().customerService();
        TestCustomers.person(customers, "C-001", "Jana", "jana@example.com");

        assertThrows(RuntimeException.class,
                () -> TestCustomers.person(storage.newContext().customerService(), "C-001", "Petr",
                        "petr@example.com"));
        assertEquals("Jana Test", storage.newContext().customerService().findCustomer("C-001").getDisplayName());
    }
}
