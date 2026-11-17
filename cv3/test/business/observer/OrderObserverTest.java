package business.observer;

import business.domainmodel.Order;
import business.domainmodel.OrderSpecification;
import business.domainmodel.OrderStatus;
import business.repository.UnitOfWorkControl;
import business.service.OrderService;
import configuration.StorageType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import support.CountingStorage;
import support.IsolatedJsonStorage;
import support.TestCustomers;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** CV09 Observer: the order (Subject) notifies registered observers of saved status changes only. */
class OrderObserverTest {
    @TempDir
    Path dir;
    private IsolatedJsonStorage jsonStorage;
    private CountingStorage storage;
    private final List<String> production = new ArrayList<>();
    private final List<String> expedition = new ArrayList<>();
    private ProductionObserver productionObserver;
    private ExpeditionObserver expeditionObserver;

    @BeforeEach
    void setUp() {
        jsonStorage = new IsolatedJsonStorage(dir);
        storage = CountingStorage.open(StorageType.JSON, dir);
        TestCustomers.person(storage.customers, "C-001", "Jana", "jana@example.com");
        storage.materials.createMaterial("M-OAK", "Dub");
        productionObserver = new ProductionObserver(production::add);
        expeditionObserver = new ExpeditionObserver(expedition::add);
        storage.orders.registerObserver(productionObserver);
        storage.orders.registerObserver(expeditionObserver);
    }

    @AfterEach
    void tearDown() {
        jsonStorage.close();
    }

    @Test
    void productionObserverIsNotifiedWhenTheOrderIsConfirmed() {
        Order order = newOrder("O-001");

        storage.orders.confirmOrder("O-001");

        // Loaded from storage, the order has no number in memory, so the message names its ID.
        assertEquals(List.of("Objednávka O-001 potvrzena – připravit výrobu"), production);
        assertTrue(expedition.isEmpty(), "expedition reacts only to PRIPRAVENA_K_EXPEDICI");
        assertEquals(OrderStatus.POTVRZENA, storage.orders.findOrder(order.getOrderId()).getStatus());
    }

    @Test
    void messageUsesTheOrderNumberWhenTheOrderHasOne() {
        Order order = newOrder("O-001");
        order.registerObserver(productionObserver);

        order.confirm();
        order.publishStatusChanges();

        assertEquals(List.of("Objednávka " + order.getNumber() + " potvrzena – připravit výrobu"), production);
    }

    @Test
    void expeditionObserverIsNotifiedWhenTheOrderIsReadyForExpedition() {
        newOrder("O-001");
        storage.orders.confirmOrder("O-001");
        storage.orders.startProduction("O-001");
        assertTrue(expedition.isEmpty());

        storage.orders.prepareForExpedition("O-001");

        assertEquals(List.of("Objednávka O-001 připravena k expedici"), expedition);
        assertEquals(1, production.size(), "production heard only the confirmation");
    }

    @Test
    void unregisteredObserverGetsNoMoreNotifications() {
        newOrder("O-001");
        newOrder("O-002");
        storage.orders.confirmOrder("O-001");
        assertEquals(1, production.size());

        storage.orders.unregisterObserver(productionObserver);
        storage.orders.confirmOrder("O-002");

        assertEquals(1, production.size());
        assertEquals(OrderStatus.POTVRZENA, storage.orders.findOrder("O-002").getStatus());
    }

    @Test
    void invalidTransitionNotifiesNobodyAndChangesNothing() {
        newOrder("O-001");

        assertThrows(IllegalStateException.class, () -> storage.orders.prepareForExpedition("O-001"));

        assertTrue(production.isEmpty());
        assertTrue(expedition.isEmpty());
        assertEquals(OrderStatus.CEKA_NA_POTVRZENI, storage.orders.findOrder("O-001").getStatus());
    }

    @Test
    void failedSaveNotifiesNobody() {
        newOrder("O-001");
        UnitOfWorkControl failing = new UnitOfWorkControl() {
            @Override public void commit() { throw new IllegalStateException("Simulovaná chyba při ukládání"); }
            @Override public void rollback() { storage.unitOfWork.rollback(); }
            @Override public boolean hasChanges() { return storage.unitOfWork.hasChanges(); }
        };
        OrderService service = new OrderService(storage.repositories.orders(), storage.customers, storage.materials,
                failing);
        service.registerObserver(productionObserver);

        assertThrows(IllegalStateException.class, () -> service.confirmOrder("O-001"));

        assertTrue(production.isEmpty(), "observers hear only about saved changes");
        assertEquals(OrderStatus.CEKA_NA_POTVRZENI, storage.orders.findOrder("O-001").getStatus());
    }

    private Order newOrder(String id) {
        return storage.orders.createOrder(id, "C-001", "M-OAK",
                OrderSpecification.builder().width(120).height(75).depth(60).designNotes("Stůl").build());
    }
}
