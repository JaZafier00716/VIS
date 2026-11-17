package business.service;

import business.domainmodel.Customer;
import configuration.StorageType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import support.CountingStorage;
import support.IsolatedJsonStorage;
import support.TestCustomers;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * Identity Map in the Data Mapper (data access layer), scoped to one Unit of Work (cv05 p.9–10):
 * one instance and one read per ID until the unit of work ends with commit() or rollback().
 */
class IdentityMapIntegrationTest {
    @TempDir
    Path dir;
    private IsolatedJsonStorage jsonStorage;
    private CountingStorage storage;

    @BeforeEach
    void setUp() {
        jsonStorage = new IsolatedJsonStorage(dir);
    }

    @AfterEach
    void tearDown() {
        jsonStorage.close();
    }

    private void open(StorageType type) {
        TestCustomers.person(CountingStorage.open(type, dir).customers, "C-001", "Jana", "jana@example.com");
        TestCustomers.person(CountingStorage.open(type, dir).customers, "C-002", "Petr", "petr@example.com");
        storage = CountingStorage.open(type, dir);
    }

    @ParameterizedTest
    @EnumSource(StorageType.class)
    void secondLookupIsServedFromTheIdentityMap(StorageType type) {
        open(type);
        Customer first = storage.customers.findCustomer("C-001");
        Customer second = storage.customers.findCustomer("C-001");

        assertSame(first, second);
        assertEquals(1, storage.customerGateway.reads, "the second lookup does not read the storage");
    }

    @ParameterizedTest
    @EnumSource(StorageType.class)
    void findAllReusesTheInstanceAlreadyInTheIdentityMap(StorageType type) {
        open(type);
        Customer loaded = storage.customers.findCustomer("C-001");

        Customer fromList = storage.customers.findAll().stream()
                .filter(c -> c.getCustomerId().equals("C-001")).findFirst().orElseThrow();

        assertSame(loaded, fromList);
        assertSame(fromList, storage.customers.findAll().stream()
                .filter(c -> c.getCustomerId().equals("C-001")).findFirst().orElseThrow());
    }

    @ParameterizedTest
    @EnumSource(StorageType.class)
    void commitEndsTheUnitOfWorkAndItsIdentityMap(StorageType type) {
        open(type);
        Customer before = storage.customers.findCustomer("C-001");
        TestCustomers.person(storage.customers, "C-003", "Eva", "eva@example.com");

        Customer after = storage.customers.findCustomer("C-001");

        assertNotSame(before, after, "no global cache: a new unit of work loads a new instance");
        assertEquals(2, storage.customerGateway.reads);
    }

    @ParameterizedTest
    @EnumSource(StorageType.class)
    void rollbackEndsTheUnitOfWorkAndItsIdentityMap(StorageType type) {
        open(type);
        Customer before = storage.customers.findCustomer("C-001");
        storage.unitOfWork.rollback();

        Customer after = storage.customers.findCustomer("C-001");

        assertNotSame(before, after);
        assertEquals(2, storage.customerGateway.reads);
    }
}
