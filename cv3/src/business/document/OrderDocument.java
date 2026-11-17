package business.document;

import business.domainmodel.Customer;
import business.domainmodel.Order;
import lombok.Getter;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * CV09 Factory Method – the Product: a document made for one order. Clients work with this type only; which
 * concrete document they get is decided by the {@link DocumentCreator} they use.
 */
@Getter
public abstract class OrderDocument {
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("d. M. yyyy");

    private final Order order;

    protected OrderDocument(Order order) {
        if (order == null) {
            throw new IllegalArgumentException("Dokument musí mít objednávku.");
        }
        this.order = order;
    }

    /** Czech title, e.g. "Faktura". */
    public abstract String getTitle();

    /** The multi-line Czech text of the document. */
    public abstract String getContent();

    /** How the order is referred to in every document. */
    protected String orderReference() {
        return order.getNumber() == null ? "Objednávka: " + order.getOrderId()
                : "Objednávka č. " + order.getNumber() + " (ID " + order.getOrderId() + ")";
    }

    protected String customerName() {
        Customer customer = order.getCustomer();
        return customer != null ? customer.getDisplayName() : order.getCustomerId();
    }

    protected String createdDate() {
        return DATE.format(order.getCreatedAt().atZone(ZoneId.systemDefault()));
    }
}
