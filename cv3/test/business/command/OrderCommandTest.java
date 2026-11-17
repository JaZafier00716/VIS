package business.command;

import business.domainmodel.OrderSpecification;
import business.domainmodel.OrderStatus;
import business.observer.ExpeditionObserver;
import business.observer.ProductionObserver;
import business.service.OrderService;
import configuration.ApplicationContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import support.IsolatedJsonStorage;
import support.TestCustomers;

import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** CV09 Command: operations run as objects through one executor that logs every result. */
class OrderCommandTest {
    @TempDir
    Path dir;
    private IsolatedJsonStorage jsonStorage;
    private ApplicationContext context;
    private OrderService orders;
    private final List<String> messages = new ArrayList<>();
    private final OrderCommandExecutor executor = new OrderCommandExecutor();

    @BeforeEach
    void setUp() {
        jsonStorage = new IsolatedJsonStorage(dir);
        context = jsonStorage.newContext();
        orders = context.orderService();
        orders.registerObserver(new ProductionObserver(messages::add));
        orders.registerObserver(new ExpeditionObserver(messages::add));
        TestCustomers.person(context.customerService(), "C-001", "Jana", "jana@example.com");
        context.materialService().createMaterial("M-OAK", "Dub");
        orders.createOrder("O-001", "C-001", "M-OAK",
                OrderSpecification.builder().width(120).height(75).depth(60).designNotes("Stůl").build());
    }

    @AfterEach
    void tearDown() {
        jsonStorage.close();
    }

    @Test
    void commandsInOrderMoveTheOrderThroughItsStatusesAndAreLogged() {
        Instant before = Instant.now();

        executor.execute(new ConfirmOrderCommand(orders, "O-001"));
        assertEquals(OrderStatus.POTVRZENA, orders.findOrder("O-001").getStatus());
        executor.execute(new StartProductionCommand(orders, "O-001"));
        assertEquals(OrderStatus.VE_VYROBE, orders.findOrder("O-001").getStatus());
        executor.execute(new PrepareExpeditionCommand(orders, "O-001"));
        assertEquals(OrderStatus.PRIPRAVENA_K_EXPEDICI, orders.findOrder("O-001").getStatus());

        List<CommandLogEntry> log = executor.getLog();
        assertEquals(List.of("Potvrdit objednávku", "Zahájit výrobu", "Připravit k expedici"),
                log.stream().map(CommandLogEntry::operation).toList());
        for (CommandLogEntry entry : log) {
            assertNotNull(entry.time());
            assertFalse(entry.time().isBefore(before));
            assertEquals("O-001", entry.orderId());
            assertTrue(entry.success());
            assertEquals("OK", entry.message());
        }
        // The Observer fires through the service after each save.
        assertEquals(List.of("Objednávka O-001 potvrzena – připravit výrobu",
                "Objednávka O-001 připravena k expedici"), messages);
        assertThrows(UnsupportedOperationException.class, () -> executor.getLog().clear());
    }

    @Test
    void invalidTransitionIsRejectedAndLoggedWithoutNotification() {
        CommandLogEntry entry = executor.execute(new StartProductionCommand(orders, "O-001"));

        assertFalse(entry.success());
        assertEquals("Zahájit výrobu", entry.operation());
        assertEquals("O-001", entry.orderId());
        assertTrue(entry.message().contains("nelze"), entry.message());
        assertEquals(List.of(entry), executor.getLog());
        assertEquals(OrderStatus.CEKA_NA_POTVRZENI, orders.findOrder("O-001").getStatus());
        assertTrue(messages.isEmpty());
    }

    @Test
    void statusSurvivesAReloadFromStorage() {
        executor.execute(new ConfirmOrderCommand(orders, "O-001"));
        executor.execute(new StartProductionCommand(orders, "O-001"));
        executor.execute(new PrepareExpeditionCommand(orders, "O-001"));

        // A fresh application (new Unit of Work and Identity Map) reads the files again.
        assertEquals(OrderStatus.PRIPRAVENA_K_EXPEDICI,
                jsonStorage.newContext().orderService().findOrder("O-001").getStatus());
    }
}
