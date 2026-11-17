package business.service;

import business.domainmodel.OrderSpecification;
import configuration.ApplicationContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
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

/** CV06 - Embedded Value: OrderSpecification has no ID and is stored in the order's own record. */
class EmbeddedValueTest {
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
    void specificationRoundTripsThroughTheOrderRecord() throws Exception {
        ApplicationContext context = storage.newContext();
        TestCustomers.person(context.customerService(), "C-001", "Jana", "jana@example.com");
        context.materialService().createMaterial("M-OAK", "Dub");
        OrderSpecification spec = OrderSpecification.builder().width(180.5).height(76).depth(90)
                .designNotes("Pracovní stůl").build();
        context.orderService().createOrder("O-001", "C-001", "M-OAK", spec);

        assertEquals(spec, storage.newContext().orderService().findOrder("O-001").getSpecification());
        Path store = dir.resolve("json-cti");
        String orderFile = Files.readString(store.resolve("orders.json"));
        assertTrue(orderFile.contains("\"width\" : 180.5"));
        try (var files = Files.list(store)) {
            assertFalse(files.anyMatch(f -> f.getFileName().toString().contains("specification")),
                    "no separate storage for the value object");
        }
    }

    @Test
    void invalidDimensionsAreRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> OrderSpecification.builder().width(0).height(10).depth(10).build());
    }
}
