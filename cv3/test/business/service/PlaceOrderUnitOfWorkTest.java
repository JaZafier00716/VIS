package business.service;

import business.domainmodel.Address;
import business.domainmodel.Customer;
import business.domainmodel.Order;
import business.domainmodel.OrderSpecification;
import business.domainmodel.OrderStatus;
import business.domainmodel.PersonCustomer;
import business.service.PlaceOrderService.AddressData;
import business.service.PlaceOrderService.CustomerData;
import configuration.StorageType;
import dataaccess.h2.H2Database;
import dataaccess.h2.H2Storage;
import dataaccess.identity.IdentityMap;
import dataaccess.mapper.DataMappers;
import dataaccess.persistence.JsonStorage;
import dataaccess.gateway.Gateways;
import dataaccess.unitofwork.GatewayEntityPersister;
import dataaccess.unitofwork.StorageTransaction;
import dataaccess.unitofwork.UnitOfWork;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import support.IsolatedJsonStorage;
import support.TestCustomers;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * CV05 scenario A: customer, address and order are one business operation saved by one Unit of Work commit,
 * on both storages.
 */
class PlaceOrderUnitOfWorkTest {
    private static final AddressData ADDRESS = new AddressData("Hlavní 1", "Ostrava", "70800", "CZ");

    @TempDir
    Path dir;
    private IsolatedJsonStorage jsonStorage;

    @BeforeEach
    void setUp() {
        jsonStorage = new IsolatedJsonStorage(dir);
    }

    @AfterEach
    void tearDown() {
        jsonStorage.close();
    }

    @ParameterizedTest
    @EnumSource(StorageType.class)
    void newCustomerAddressAndOrderAreCommittedInOneTransaction(StorageType type) {
        seed(type);
        Application app = application(type, false);

        Order order = app.placeOrder.placeOrder(TestCustomers.newPerson("Petr", "petr@example.com"), ADDRESS,
                "M-OAK", spec());

        assertEquals(1, app.transaction.begins, "one transaction");
        assertEquals(1, app.transaction.commits, "one commit");
        assertEquals(0, app.transaction.rollbacks);
        assertEquals(List.of(PersonCustomer.class, Address.class, Order.class), app.persister.inserted);
        assertEquals(OrderStatus.CEKA_NA_POTVRZENI, order.getStatus());

        // The caller gets a complete object graph without reloading.
        Customer customer = order.getCustomer();
        assertTrue(customer.getCustomerId().startsWith("C-"));
        assertEquals("Petr Test", customer.getDisplayName());
        Address address = customer.getAddresses().get(0);
        assertEquals(customer.getCustomerId(), address.getCustomerId());

        Gateways stored = gateways(type);
        assertTrue(stored.customers().findById(customer.getCustomerId()).isPresent());
        assertEquals(List.of(address.getAddressId()), stored.addresses().findByCustomerId(customer.getCustomerId())
                .stream().map(a -> a.addressId()).toList());
        assertEquals(customer.getCustomerId(), stored.orders().findById(order.getOrderId()).orElseThrow().customerId());
    }

    @ParameterizedTest
    @EnumSource(StorageType.class)
    void failingOrderInsertStoresNeitherCustomerNorAddress(StorageType type) {
        seed(type);
        Application app = application(type, true);

        assertThrows(IllegalStateException.class, () -> app.placeOrder.placeOrder(
                TestCustomers.newPerson("Petr", "petr@example.com"), ADDRESS, "M-OAK", spec()));

        assertEquals(1, app.transaction.rollbacks, "the storage transaction was rolled back");
        assertFalse(app.unitOfWork.hasChanges(), "the business operation was abandoned");
        Gateways stored = gateways(type);
        assertEquals(List.of("C-001"), stored.customers().findAll().stream().map(Customer::getCustomerId).toList());
        assertTrue(stored.addresses().findAll().isEmpty());
        assertTrue(stored.orders().findAll().isEmpty());
    }

    @ParameterizedTest
    @EnumSource(StorageType.class)
    void invalidInputIsRejectedBeforeAnythingIsRegistered(StorageType type) {
        seed(type);
        Application app = application(type, false);
        PlaceOrderService service = app.placeOrder;

        List<Executable> rejected = List.of(
                () -> service.placeOrder(CustomerData.existing("C-404"), ADDRESS, "M-OAK", spec()),
                // An unknown ID is rejected: a client-supplied ID never creates a customer (new ones carry no ID).
                () -> service.placeOrder(CustomerData.existing("C-NEW"), ADDRESS, "M-OAK", spec()),
                () -> service.placeOrder(TestCustomers.newPerson("Petr", "bez-zavinace"), ADDRESS, "M-OAK", spec()),
                () -> service.placeOrder(TestCustomers.newPerson("Petr", "petr@example.com"),
                        new AddressData("", "", null, null), "M-OAK", spec()),
                () -> service.placeOrder(TestCustomers.newPerson("Petr", "petr@example.com"), ADDRESS, "M-404",
                        spec()),
                () -> service.placeOrder(TestCustomers.newPerson("Petr", "petr@example.com"), ADDRESS, "M-OAK",
                        null));
        for (Executable call : rejected) {
            assertThrows(IllegalArgumentException.class, call);
            assertFalse(app.unitOfWork.hasChanges(), "nothing was registered");
        }

        assertEquals(0, app.transaction.begins, "no transaction was started");
        Gateways stored = gateways(type);
        assertEquals(1, stored.customers().findAll().size());
        assertTrue(stored.addresses().findAll().isEmpty());
        assertTrue(stored.orders().findAll().isEmpty());
    }

    @ParameterizedTest
    @EnumSource(StorageType.class)
    void existingCustomerGetsOnlyANewAddressAndOrder(StorageType type) {
        seed(type);
        String before = snapshot(gateways(type).customers().findById("C-001").orElseThrow());
        Application app = application(type, false);

        Order order = app.placeOrder.placeOrder(CustomerData.existing("C-001"), ADDRESS, "M-OAK", spec());

        assertEquals(List.of(Address.class, Order.class), app.persister.inserted);
        assertTrue(app.persister.updated.isEmpty());
        assertEquals(1, app.transaction.commits);
        Gateways stored = gateways(type);
        assertEquals(before, snapshot(stored.customers().findById("C-001").orElseThrow()),
                "the customer record is unchanged");
        assertEquals(1, stored.addresses().findByCustomerId("C-001").size());
        assertEquals("C-001", stored.orders().findById(order.getOrderId()).orElseThrow().customerId());
        assertEquals(1, order.getCustomer().getAddresses().size());
    }

    /** Every stored value of a person customer, for comparing before and after. */
    private static String snapshot(Customer customer) {
        PersonCustomer person = (PersonCustomer) customer;
        return String.join("|", person.getCustomerId(), person.getEmail(), String.valueOf(person.getPhone()),
                String.valueOf(person.getBillingAddress()), person.getFirstName(), person.getLastName(),
                String.valueOf(person.getBirthDate()));
    }

    private void seed(StorageType type) {
        Application app = application(type, false);
        TestCustomers.person(app.customers, "C-001", "Jana", "jana@example.com");
        app.materials.createMaterial("M-OAK", "Dub");
    }

    private Gateways gateways(StorageType type) {
        return type == StorageType.H2 ? H2Storage.gateways(new H2Database(dir.resolve("vis-h2")))
                : JsonStorage.gateways();
    }

    /** The services wired like {@code ApplicationContext}, with a counted transaction and a recording persister. */
    private Application application(StorageType type, boolean failOnOrder) {
        Gateways gateways;
        StorageTransaction delegate;
        if (type == StorageType.H2) {
            H2Database database = new H2Database(dir.resolve("vis-h2"));
            gateways = H2Storage.gateways(database);
            delegate = H2Storage.transaction(database);
        } else {
            gateways = JsonStorage.gateways();
            delegate = JsonStorage.transaction();
        }
        return new Application(gateways, new CountingTransaction(delegate),
                new RecordingPersister(new GatewayEntityPersister(gateways), failOnOrder));
    }

    private static OrderSpecification spec() {
        return OrderSpecification.builder().width(120).height(75).depth(60).designNotes("Stůl").build();
    }

    private static final class Application {
        final CountingTransaction transaction;
        final RecordingPersister persister;
        final UnitOfWork unitOfWork;
        final CustomerService customers;
        final MaterialService materials;
        final PlaceOrderService placeOrder;

        Application(Gateways gateways, CountingTransaction transaction, RecordingPersister persister) {
            this.transaction = transaction;
            this.persister = persister;
            this.unitOfWork = new UnitOfWork(new IdentityMap(), persister, transaction);
            business.repository.Repositories repositories = DataMappers.create(gateways, unitOfWork);
            this.customers = new CustomerService(repositories.customers(), repositories.addresses(), unitOfWork);
            this.materials = new MaterialService(repositories.materials(), repositories.finishes(), unitOfWork);
            OrderService orders = new OrderService(repositories.orders(), customers, materials, unitOfWork);
            this.placeOrder = new PlaceOrderService(repositories, customers, materials, orders, unitOfWork);
        }
    }

    /** Counts how often the storage transaction is begun, committed and rolled back. */
    private static final class CountingTransaction implements StorageTransaction {
        private final StorageTransaction delegate;
        int begins;
        int commits;
        int rollbacks;

        CountingTransaction(StorageTransaction delegate) {
            this.delegate = delegate;
        }

        @Override public void begin() { begins++; delegate.begin(); }
        @Override public void commit() { commits++; delegate.commit(); }
        @Override public void rollback() { rollbacks++; delegate.rollback(); }
    }

    /** Records the written entity types; optionally fails on the order insert, after customer and address. */
    private static final class RecordingPersister implements UnitOfWork.EntityPersister {
        private final UnitOfWork.EntityPersister delegate;
        private final boolean failOnOrder;
        final List<Class<?>> inserted = new ArrayList<>();
        final List<Class<?>> updated = new ArrayList<>();

        RecordingPersister(UnitOfWork.EntityPersister delegate, boolean failOnOrder) {
            this.delegate = delegate;
            this.failOnOrder = failOnOrder;
        }

        @Override
        public void insert(Object entity) {
            if (failOnOrder && entity instanceof Order) {
                throw new IllegalStateException("Simulovaná chyba při zápisu objednávky");
            }
            delegate.insert(entity);
            inserted.add(entity.getClass());
        }

        @Override
        public void update(Object entity) {
            delegate.update(entity);
            updated.add(entity.getClass());
        }

        @Override public void delete(Object entity) { delegate.delete(entity); }
    }
}
