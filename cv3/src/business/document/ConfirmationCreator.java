package business.document;

import business.domainmodel.Order;

/** CV09 Factory Method – a concrete Creator: makes an {@link OrderConfirmation}. */
public class ConfirmationCreator extends DocumentCreator {
    @Override
    protected OrderDocument createDocument(Order order) {
        return new OrderConfirmation(order);
    }
}
