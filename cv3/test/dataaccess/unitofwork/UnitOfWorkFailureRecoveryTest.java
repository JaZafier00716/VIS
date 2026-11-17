package dataaccess.unitofwork;

import business.domainmodel.Customer;
import business.domainmodel.Material;
import dataaccess.h2.H2Database;
import dataaccess.h2.H2Storage;
import dataaccess.identity.IdentityMap;
import dataaccess.persistence.JsonStorage;
import dataaccess.gateway.Gateways;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import support.IsolatedJsonStorage;
import support.TestCustomers;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A commit that fails halfway leaves the storage exactly as it was before the commit. */
class UnitOfWorkFailureRecoveryTest {
    @TempDir
    Path dir;
    private IsolatedJsonStorage storage;

    @AfterEach
    void tearDown() {
        if (storage != null) {
            storage.close();
        }
    }

    @Test
    void jsonFilesAreRestoredAfterAFailedCommit() throws Exception {
        storage = new IsolatedJsonStorage(dir);
        Gateways gateways = JsonStorage.gateways();
        UnitOfWork setup = new UnitOfWork(new IdentityMap(), new GatewayEntityPersister(gateways),
                JsonStorage.transaction());
        setup.registerNew(customer("C-1"), "C-1");
        setup.commit();
        String before = Files.readString(dir.resolve("customers.json"));

        UnitOfWork unitOfWork = new UnitOfWork(new IdentityMap(),
                new FailingPersister(new GatewayEntityPersister(gateways)), JsonStorage.transaction());
        unitOfWork.registerNew(customer("C-2"), "C-2");
        unitOfWork.registerNew(Material.builder().materialId("M-FAIL").name("Selže").build(), "M-FAIL");

        assertThrows(IllegalStateException.class, unitOfWork::commit);
        assertEquals(before, Files.readString(dir.resolve("customers.json")));
        assertFalse(Files.exists(dir.resolve("materials.json")));
        assertTrue(unitOfWork.hasChanges(), "pending changes stay registered for a retry");
    }

    @Test
    void h2TransactionIsRolledBackAfterAFailedCommit() {
        H2Database database = new H2Database(dir.resolve("vis-h2"));
        Gateways gateways = H2Storage.gateways(database);
        UnitOfWork unitOfWork = new UnitOfWork(new IdentityMap(),
                new FailingPersister(new GatewayEntityPersister(gateways)), H2Storage.transaction(database));
        unitOfWork.registerNew(customer("C-1"), "C-1");
        unitOfWork.registerNew(Material.builder().materialId("M-FAIL").name("Selže").build(), "M-FAIL");

        assertThrows(IllegalStateException.class, unitOfWork::commit);

        assertTrue(gateways.customers().findAll().isEmpty(), "the customer insert was rolled back");
    }

    private static Customer customer(String id) {
        return TestCustomers.person(id, "Zákazník " + id, id + "@example.com");
    }

    /** Writes normally, then fails on the material - i.e. after the customer was already written. */
    private record FailingPersister(UnitOfWork.EntityPersister delegate) implements UnitOfWork.EntityPersister {
        @Override
        public void insert(Object entity) {
            if (entity instanceof Material) {
                throw new IllegalStateException("Simulovaná chyba úložiště");
            }
            delegate.insert(entity);
        }

        @Override public void update(Object entity) { delegate.update(entity); }
        @Override public void delete(Object entity) { delegate.delete(entity); }
    }
}
