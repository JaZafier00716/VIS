package business.domainmodel;

import configuration.StorageType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import support.CountingStorage;
import support.IsolatedJsonStorage;
import support.TestCustomers;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * Lazy Load (cv05 p.11–12): the mechanism is in the domain objects, the loaders are attached by the Data Mappers
 * in the data access layer. The parameterized tests prove it end to end on both storages by counting reads.
 */
class LazyLoadingTest {
    @TempDir
    Path dir;

    @Test
    void addressesAreLoadedOnFirstAccessOnly() {
        AtomicInteger calls = new AtomicInteger();
        Customer customer = TestCustomers.person("C-1", "Jana", "jana@example.com");
        customer.setAddressesLoader(() -> {
            calls.incrementAndGet();
            return List.of(Address.builder().addressId("A-1").customerId("C-1").street("Hlavní 1").city("Ostrava")
                    .build());
        });

        assertEquals(0, calls.get(), "nothing is loaded when the customer is created");
        assertEquals(1, customer.getAddresses().size());
        customer.getAddresses();
        assertEquals(1, calls.get(), "the loader runs only once");
    }

    @Test
    void addingAnAddressBeforeAccessKeepsTheStoredOnes() {
        Customer customer = TestCustomers.person("C-1", "Jana", "jana@example.com");
        customer.setAddressesLoader(() -> List.of(Address.builder().addressId("A-1").customerId("C-1")
                .street("Hlavní 1").city("Ostrava").build()));

        customer.addAddress(Address.builder().addressId("A-2").customerId("C-1").street("Nová 2").city("Brno").build());

        assertEquals(List.of("A-1", "A-2"), customer.getAddresses().stream().map(Address::getAddressId).toList());
    }

    @Test
    void orderCustomerIsLoadedLazilyAndCached() {
        AtomicInteger calls = new AtomicInteger();
        Customer customer = TestCustomers.person("C-1", "Jana", "jana@example.com");
        Order order = new Order("O-1", "C-1", "M-1",
                OrderSpecification.builder().width(1).height(1).depth(1).build(), Instant.now());
        order.setCustomerLoader(() -> {
            calls.incrementAndGet();
            return customer;
        });

        assertEquals(0, calls.get());
        assertSame(customer, order.getCustomer());
        assertSame(customer, order.getCustomer());
        assertEquals(1, calls.get());
    }

    @ParameterizedTest
    @EnumSource(StorageType.class)
    void loadingAnOrderDoesNotReadItsCustomerUntilFirstAccess(StorageType type) {
        try (IsolatedJsonStorage ignored = new IsolatedJsonStorage(dir)) {
            CountingStorage seed = CountingStorage.open(type, dir);
            TestCustomers.person(seed.customers, "C-1", "Jana", "jana@example.com");
            seed.materials.createMaterial("M-1", "Dub");
            seed.orders.createOrder("O-1", "C-1", "M-1",
                    OrderSpecification.builder().width(1).height(1).depth(1).build());
            CountingStorage storage = CountingStorage.open(type, dir);

            Order order = storage.orders.findOrder("O-1");
            assertEquals(0, storage.customerGateway.reads, "the customer is not loaded with the order");

            Customer customer = order.getCustomer();
            assertEquals("Jana Test", customer.getDisplayName());
            assertEquals(1, storage.customerGateway.reads, "first access loads the customer");

            assertSame(customer, order.getCustomer());
            assertSame(customer, storage.customers.findCustomer("C-1"), "the lazy loader used the Identity Map");
            assertEquals(1, storage.customerGateway.reads, "repeated access reads nothing");
        }
    }

    @ParameterizedTest
    @EnumSource(StorageType.class)
    void loadingACustomerDoesNotReadTheAddressesUntilFirstAccess(StorageType type) {
        try (IsolatedJsonStorage ignored = new IsolatedJsonStorage(dir)) {
            CountingStorage seed = CountingStorage.open(type, dir);
            TestCustomers.person(seed.customers, "C-1", "Jana", "jana@example.com");
            seed.customers.addAddress("C-1", "Hlavní 1", "Ostrava", "70800", "CZ");
            CountingStorage storage = CountingStorage.open(type, dir);

            Customer customer = storage.customers.findCustomer("C-1");
            assertEquals(0, storage.addressGateway.reads, "the addresses are not loaded with the customer");

            assertEquals(List.of("Ostrava"), customer.getAddresses().stream().map(Address::getCity).toList());
            assertEquals(1, storage.addressGateway.reads, "first access loads the addresses");

            customer.getAddresses();
            assertEquals(1, storage.addressGateway.reads, "repeated access reads nothing");
        }
    }
}
