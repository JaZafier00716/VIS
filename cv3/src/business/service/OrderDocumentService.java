package business.service;

import business.document.DocumentCreator;
import business.document.OrderDocument;
import lombok.RequiredArgsConstructor;

/**
 * CV09 Factory Method client: loads the order and lets the chosen creator make the document. It only reads the
 * order, so no Unit of Work commit is needed.
 */
@RequiredArgsConstructor
public class OrderDocumentService {
    private final OrderService orders;

    public OrderDocument createDocument(String orderId, DocumentCreator creator) {
        if (creator == null) {
            throw new IllegalArgumentException("Typ dokumentu je povinný.");
        }
        return creator.produce(orders.findOrder(orderId));
    }
}
