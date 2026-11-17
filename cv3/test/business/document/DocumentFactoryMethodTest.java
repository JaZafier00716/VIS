package business.document;

import business.domainmodel.Money;
import business.domainmodel.Order;
import business.domainmodel.OrderSpecification;
import business.service.OrderDocumentService;
import configuration.ApplicationContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import support.IsolatedJsonStorage;
import support.TestCustomers;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** CV09 Factory Method: each creator decides which document is made; the common logic is shared. */
class DocumentFactoryMethodTest {
    @TempDir
    Path dir;

    private static Order order() {
        OrderSpecification spec = OrderSpecification.builder().width(100).height(80).depth(50)
                .designNotes("bez úchytek").build();
        Order order = new Order("O-77", "C-9", "M-OAK", spec, Instant.now());
        order.addItem("M-OAK", 2, Money.czk(1000), null);
        order.addItem("M-PINE", 3, Money.czk(500), null);
        return order;
    }

    @Test
    void confirmationCreatorMakesAnOrderConfirmation() {
        OrderDocument document = new ConfirmationCreator().produce(order());
        assertInstanceOf(OrderConfirmation.class, document);
        assertEquals("Potvrzení objednávky", document.getTitle());
        assertTrue(document.getContent().contains("O-77"));
        assertTrue(document.getContent().contains("Zákazník: C-9"), "no customer loaded → its ID is shown");
        assertTrue(document.getContent().contains("M-OAK"));
    }

    @Test
    void deliveryNoteCreatorListsItemsAndQuantities() {
        OrderDocument document = new DeliveryNoteCreator().produce(order());
        assertInstanceOf(DeliveryNote.class, document);
        assertEquals("Dodací list", document.getTitle());
        String content = document.getContent();
        assertTrue(content.contains("1. M-OAK – 2 ks"));
        assertTrue(content.contains("2. M-PINE – 3 ks"));
        assertTrue(content.contains("Celkem kusů: 5"));
    }

    @Test
    void invoiceIsAddedWithoutChangingTheCommonLogic() {
        OrderDocument document = new InvoiceCreator().produce(order());
        assertInstanceOf(Invoice.class, document);
        assertEquals("Faktura", document.getTitle());
        assertTrue(document.getContent().contains("1000.00 CZK × 2 ks = 2000.00 CZK"));
        assertTrue(document.getContent().contains("Celkem k úhradě: 3500.00 CZK"));
    }

    @Test
    void allDocumentsAreHandledAsOrderDocumentInOneLoop() {
        Order order = order();
        List<String> titles = new ArrayList<>();
        for (DocumentCreator creator : List.of(new ConfirmationCreator(), new DeliveryNoteCreator(),
                new InvoiceCreator())) {
            OrderDocument document = creator.produce(order);
            assertSame(order, document.getOrder());
            assertTrue(document.getContent().contains("O-77"));
            assertTrue(creator.render(order).startsWith(document.getTitle() + "\n"));
            titles.add(document.getTitle());
        }
        assertEquals(List.of("Potvrzení objednávky", "Dodací list", "Faktura"), titles);
    }

    @Test
    void commonLogicRejectsAMissingOrder() {
        assertThrows(IllegalArgumentException.class, () -> new ConfirmationCreator().produce(null));
    }

    @Test
    void serviceLoadsTheStoredOrderAndShowsTheCustomersName() {
        try (IsolatedJsonStorage storage = new IsolatedJsonStorage(dir)) {
            ApplicationContext context = storage.newContext();
            TestCustomers.person(context.customerService(), "C-001", "Jana", "jana@example.com");
            context.materialService().createMaterial("M-OAK", "Dub");
            OrderSpecification spec = OrderSpecification.builder().width(100).height(80).depth(50).build();
            context.orderService().createOrder("O-001", "C-001", "M-OAK", spec);
            context.orderService().addItem("O-001", "M-OAK", 2, new BigDecimal("1000.00"), null);

            OrderDocumentService documents = new OrderDocumentService(storage.newContext().orderService());
            OrderDocument confirmation = documents.createDocument("O-001", new ConfirmationCreator());
            assertTrue(confirmation.getContent().contains("O-001"));
            assertTrue(confirmation.getContent().contains("Zákazník: Jana Test"));
            assertInstanceOf(DeliveryNote.class, documents.createDocument("O-001", new DeliveryNoteCreator()));
            assertThrows(IllegalArgumentException.class, () -> documents.createDocument("O-404",
                    new InvoiceCreator()));
        }
    }
}
