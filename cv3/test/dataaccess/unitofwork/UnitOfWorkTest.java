package dataaccess.unitofwork;

import business.domainmodel.Address;
import business.domainmodel.Customer;
import business.domainmodel.Material;
import business.domainmodel.Order;
import business.domainmodel.OrderSpecification;
import dataaccess.identity.IdentityMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import support.TestCustomers;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UnitOfWorkTest {
    private TrackingPersister persister;
    private List<String> transaction;
    private UnitOfWork unitOfWork;

    @BeforeEach
    void setUp() {
        persister = new TrackingPersister();
        transaction = new ArrayList<>();
        unitOfWork = new UnitOfWork(new IdentityMap(), persister, new StorageTransaction() {
            @Override public void begin() { transaction.add("begin"); }
            @Override public void commit() { transaction.add("commit"); }
            @Override public void rollback() { transaction.add("rollback"); }
        });
    }

    @Test
    void insertsReferencedRecordsFirstAndDeletesInReverse() {
        Order order = order();
        Material material = Material.builder().materialId("M-1").name("Dub").build();
        Customer customer = customer();
        unitOfWork.registerNew(order, "O-1");
        unitOfWork.registerNew(material, "M-1");
        unitOfWork.registerNew(customer, "C-1");

        unitOfWork.commit();

        assertEquals(List.of("insert C-1", "insert M-1", "insert O-1"), persister.calls);
        assertEquals(List.of("begin", "commit"), transaction);

        persister.calls.clear();
        unitOfWork.registerDeleted(customer, "C-1");
        unitOfWork.registerDeleted(order, "O-1");
        unitOfWork.commit();
        assertEquals(List.of("delete O-1", "delete C-1"), persister.calls);
    }

    @Test
    void customerAddressAndOrderAreOrderedByTheUnitOfWorkNotByRegistration() {
        Customer customer = customer();
        Address address = Address.builder().addressId("A-1").customerId("C-1").street("Hlavní 1").city("Ostrava")
                .build();
        Order order = order();
        unitOfWork.registerNew(order, "O-1");
        unitOfWork.registerNew(address, "A-1");
        unitOfWork.registerNew(customer, "C-1");

        unitOfWork.commit();

        assertEquals(List.of("insert C-1", "insert A-1", "insert O-1"), persister.calls);

        persister.calls.clear();
        unitOfWork.registerDeleted(customer, "C-1");
        unitOfWork.registerDeleted(address, "A-1");
        unitOfWork.registerDeleted(order, "O-1");
        unitOfWork.commit();
        assertEquals(List.of("delete O-1", "delete A-1", "delete C-1"), persister.calls);
    }

    @Test
    void newThenDeletedIsNeverWritten() {
        unitOfWork.registerNew(customer(), "C-1");
        unitOfWork.registerDeleted(customer(), "C-1");

        unitOfWork.commit();

        assertTrue(persister.calls.isEmpty());
        assertTrue(transaction.isEmpty(), "no transaction without changes");
    }

    @Test
    void dirtyNewObjectIsStillInsertedOnce() {
        Customer customer = customer();
        unitOfWork.registerNew(customer, "C-1");
        unitOfWork.registerDirty(customer, "C-1");

        unitOfWork.commit();

        assertEquals(List.of("insert C-1"), persister.calls);
        assertFalse(unitOfWork.hasChanges());
    }

    @Test
    void registeredObjectIsAvailableFromTheIdentityMap() {
        Customer customer = customer();
        unitOfWork.registerNew(customer, "C-1");

        assertSame(customer, unitOfWork.get(Customer.class, "C-1").orElseThrow());
        unitOfWork.rollback();
        assertTrue(unitOfWork.get(Customer.class, "C-1").isEmpty());
    }

    private static Customer customer() {
        return TestCustomers.person("C-1", "Jana", "jana@example.com");
    }

    private static Order order() {
        return new Order("O-1", "C-1", "M-1", OrderSpecification.builder().width(1).height(1).depth(1).build(),
                Instant.now());
    }

    static class TrackingPersister implements UnitOfWork.EntityPersister {
        final List<String> calls = new ArrayList<>();

        @Override public void insert(Object entity) { calls.add("insert " + id(entity)); }
        @Override public void update(Object entity) { calls.add("update " + id(entity)); }
        @Override public void delete(Object entity) { calls.add("delete " + id(entity)); }

        private static String id(Object entity) {
            if (entity instanceof Customer c) return c.getCustomerId();
            if (entity instanceof Material m) return m.getMaterialId();
            if (entity instanceof Order o) return o.getOrderId();
            if (entity instanceof Address a) return a.getAddressId();
            return entity.toString();
        }
    }
}
