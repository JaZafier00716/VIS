package business.document;

import business.domainmodel.Order;

/** CV09 Factory Method – the extension: a new creator for {@link Invoice}; the common logic stays untouched. */
public class InvoiceCreator extends DocumentCreator {
    @Override
    protected OrderDocument createDocument(Order order) {
        return new Invoice(order);
    }
}
