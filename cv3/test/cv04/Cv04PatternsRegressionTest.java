package cv04;

import business.domainmodel.Order;
import business.domainmodel.OrderStatus;
import business.service.PlaceOrderService.AddressData;
import configuration.ApplicationContext;
import configuration.StorageType;
import cv04.activerecord.CustomerRecord;
import cv04.activerecord.MaterialRecord;
import cv04.activerecord.OrderRecord;
import cv04.datamapper.DataMapperDemonstration;
import cv04.rdg.business.RdgOrderService;
import cv04.rdg.dataaccess.CustomerRowGateway;
import cv04.rdg.dataaccess.MaterialRowGateway;
import cv04.rdg.dataaccess.OrderRowGateway;
import cv04.tdg.business.TdgOrderService;
import cv04.tdg.dataaccess.CustomerTableGateway;
import cv04.tdg.dataaccess.MaterialDto;
import cv04.tdg.dataaccess.MaterialTableGateway;
import cv04.tdg.dataaccess.OrderDto;
import cv04.tdg.dataaccess.OrderTableGateway;
import dataaccess.h2.H2Database;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import support.IsolatedJsonStorage;
import support.TestCustomers;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The CV04 use case (cv04 p.2) with each of the four patterns: a valid order is stored in CEKA_NA_POTVRZENI and
 * found by ID; an order with dimensions &lt;= 0 or a missing material is rejected and stores nothing.
 */
class Cv04PatternsRegressionTest {
    @TempDir
    Path dir;
    H2Database database;

    @BeforeEach
    void setUp() {
        database = Cv04Schema.database(dir);
        new MaterialTableGateway(database).insert(new MaterialDto("M-OAK", "Dub"));
    }

    /** Counts through plain table gateways, independent of the pattern under test. */
    void assertNothingStored() {
        assertTrue(new CustomerTableGateway(database).findAll().isEmpty(), "no customer stored");
        assertTrue(new OrderTableGateway(database).findAll().isEmpty(), "no order stored");
    }

    @Nested
    class TableDataGateway {
        @Test
        void validOrderIsStoredWaitingForConfirmation() {
            OrderDto order = new TdgOrderService(database).placeOrder("Jana", "jana@example.com", "M-OAK",
                    120, 75, 60, "Stůl");

            assertEquals(Cv04Schema.STATUS_WAITING, order.status());
            assertEquals(order, new OrderTableGateway(database).findById(order.orderId()).orElseThrow());
            assertEquals("Jana", new CustomerTableGateway(database).findById(order.customerId()).orElseThrow()
                    .name());
        }

        @Test
        void invalidDimensionsAreRejected() {
            assertThrows(IllegalArgumentException.class, () -> new TdgOrderService(database)
                    .placeOrder("Jana", "jana@example.com", "M-OAK", 0, 75, 60, null));
            assertNothingStored();
        }

        @Test
        void missingMaterialIsRejected() {
            assertThrows(IllegalArgumentException.class, () -> new TdgOrderService(database)
                    .placeOrder("Jana", "jana@example.com", "M-404", 120, 75, 60, null));
            assertNothingStored();
        }

        @Test
        void updateAndFindAll() {
            OrderTableGateway orders = new OrderTableGateway(database);
            OrderDto order = new TdgOrderService(database).placeOrder("Jana", "jana@example.com", "M-OAK",
                    120, 75, 60, null);
            new TdgOrderService(database).placeOrder("Petr", "petr@example.com", "M-OAK", 80, 40, 40, null);

            orders.update(new OrderDto(order.orderId(), order.customerId(), order.materialId(), 150, 75, 60,
                    "Větší stůl", "POTVRZENA", order.createdAt()));

            OrderDto updated = orders.findById(order.orderId()).orElseThrow();
            assertEquals(150, updated.width());
            assertEquals("POTVRZENA", updated.status());
            assertEquals(2, orders.findAll().size());
            new MaterialTableGateway(database).update(new MaterialDto("M-OAK", "Dub masiv"));
            assertEquals(List.of("Dub masiv"),
                    new MaterialTableGateway(database).findAll().stream().map(MaterialDto::name).toList());
        }
    }

    @Nested
    class RowDataGateway {
        @Test
        void validOrderIsStoredWaitingForConfirmation() {
            OrderRowGateway order = new RdgOrderService(database).placeOrder("Jana", "jana@example.com", "M-OAK",
                    120, 75, 60, "Stůl");

            OrderRowGateway found = OrderRowGateway.find(database, order.getOrderId()).orElseThrow();
            assertEquals(Cv04Schema.STATUS_WAITING, found.getStatus());
            assertEquals("Jana", CustomerRowGateway.find(database, found.getCustomerId()).orElseThrow().getName());
        }

        @Test
        void invalidDimensionsAreRejected() {
            assertThrows(IllegalArgumentException.class, () -> new RdgOrderService(database)
                    .placeOrder("Jana", "jana@example.com", "M-OAK", 120, -1, 60, null));
            assertNothingStored();
        }

        @Test
        void missingMaterialIsRejected() {
            assertThrows(IllegalArgumentException.class, () -> new RdgOrderService(database)
                    .placeOrder("Jana", "jana@example.com", "M-404", 120, 75, 60, null));
            assertNothingStored();
        }

        @Test
        void insertItselfChecksTheMinimalRules() {
            new CustomerRowGateway(database, "C-1", "Jana", "jana@example.com").insert();
            OrderRowGateway order = new OrderRowGateway(database, "O-1", "C-1", "M-404", 120, 75, 60, null,
                    Cv04Schema.STATUS_WAITING, java.time.Instant.now());

            assertThrows(IllegalArgumentException.class, order::insert);
            assertTrue(OrderRowGateway.findAll(database).isEmpty());
        }

        @Test
        void updateAndFindAll() {
            OrderRowGateway order = new RdgOrderService(database).placeOrder("Jana", "jana@example.com", "M-OAK",
                    120, 75, 60, null);
            new RdgOrderService(database).placeOrder("Petr", "petr@example.com", "M-OAK", 80, 40, 40, null);

            order.setWidth(150);
            order.setStatus("POTVRZENA");
            order.update();
            CustomerRowGateway customer = CustomerRowGateway.find(database, order.getCustomerId()).orElseThrow();
            customer.setEmail("nova@example.com");
            customer.update();

            assertEquals(150, OrderRowGateway.find(database, order.getOrderId()).orElseThrow().getWidth());
            assertEquals("nova@example.com",
                    CustomerRowGateway.find(database, order.getCustomerId()).orElseThrow().getEmail());
            assertEquals(2, OrderRowGateway.findAll(database).size());
            assertEquals(2, CustomerRowGateway.findAll(database).size());
            assertEquals(1, MaterialRowGateway.findAll(database).size());
        }
    }

    @Nested
    class ActiveRecord {
        @Test
        void validOrderIsStoredWaitingForConfirmation() {
            OrderRecord order = OrderRecord.place(database, "Jana", "jana@example.com", "M-OAK", 120, 75, 60,
                    "Stůl");

            assertEquals(Cv04Schema.STATUS_WAITING, OrderRecord.find(database, order.getOrderId()).orElseThrow()
                    .getStatus());
            assertEquals("Jana", CustomerRecord.find(database, order.getCustomerId()).orElseThrow().getName());

            order.confirm();
            order.save();
            assertEquals("POTVRZENA", OrderRecord.find(database, order.getOrderId()).orElseThrow().getStatus());
        }

        @Test
        void invalidDimensionsAreRejected() {
            assertThrows(IllegalArgumentException.class,
                    () -> OrderRecord.place(database, "Jana", "jana@example.com", "M-OAK", 120, 75, 0, null));
            assertNothingStored();
        }

        @Test
        void missingMaterialIsRejected() {
            assertThrows(IllegalArgumentException.class,
                    () -> OrderRecord.place(database, "Jana", "jana@example.com", "M-404", 120, 75, 60, null));
            assertNothingStored();
            assertTrue(MaterialRecord.exists(database, "M-OAK"));
        }

        @Test
        void saveCallsValidate() {
            CustomerRecord customer = new CustomerRecord(database, "C-1", "Jana", "bez-zavinace");
            assertThrows(IllegalArgumentException.class, customer::save);
            assertNothingStored();
        }
    }

    @Nested
    class DataMapper {
        private static final AddressData ADDRESS = new AddressData("Hlavní 1", "Ostrava", "70800", "CZ");

        private ApplicationContext application(StorageType type) {
            ApplicationContext application = ApplicationContext.create(type, dir.resolve("app-" + type));
            application.materialService().createMaterial("M-OAK", "Dub");
            return application;
        }

        @ParameterizedTest
        @EnumSource(StorageType.class)
        void validOrderIsStoredWaitingForConfirmation(StorageType type) {
            try (IsolatedJsonStorage ignored = new IsolatedJsonStorage(dir.resolve("app-" + type))) {
                Order order = DataMapperDemonstration.placeOrder(application(type),
                        TestCustomers.newPerson("Jana", "jana@example.com"), "M-OAK", 120, 75, 60, "Stůl", ADDRESS);

                assertEquals(OrderStatus.CEKA_NA_POTVRZENI, order.getStatus());
                assertEquals("Jana Test", ApplicationContext.create(type, dir.resolve("app-" + type)).orderService()
                        .findOrder(order.getOrderId()).getCustomer().getDisplayName());
            }
        }

        @ParameterizedTest
        @EnumSource(StorageType.class)
        void invalidDimensionsAreRejected(StorageType type) {
            try (IsolatedJsonStorage ignored = new IsolatedJsonStorage(dir.resolve("app-" + type))) {
                ApplicationContext application = application(type);
                assertThrows(IllegalArgumentException.class, () -> DataMapperDemonstration.placeOrder(application,
                        TestCustomers.newPerson("Jana", "jana@example.com"), "M-OAK", 0, 75, 60, null, ADDRESS));
                assertTrue(application.customerService().findAll().isEmpty());
                assertTrue(application.orderService().findAll().isEmpty());
            }
        }

        @ParameterizedTest
        @EnumSource(StorageType.class)
        void missingMaterialIsRejected(StorageType type) {
            try (IsolatedJsonStorage ignored = new IsolatedJsonStorage(dir.resolve("app-" + type))) {
                ApplicationContext application = application(type);
                assertThrows(IllegalArgumentException.class, () -> DataMapperDemonstration.placeOrder(application,
                        TestCustomers.newPerson("Jana", "jana@example.com"), "M-404", 120, 75, 60, null, ADDRESS));
                assertTrue(application.customerService().findAll().isEmpty());
                assertTrue(application.orderService().findAll().isEmpty());
            }
        }
    }
}
