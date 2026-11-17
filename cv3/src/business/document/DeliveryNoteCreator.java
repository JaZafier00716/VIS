package business.document;

import business.domainmodel.Order;

/** CV09 Factory Method – a concrete Creator: makes a {@link DeliveryNote}. */
public class DeliveryNoteCreator extends DocumentCreator {
    @Override
    protected OrderDocument createDocument(Order order) {
        return new DeliveryNote(order);
    }
}
