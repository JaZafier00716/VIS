package business.document;

import business.domainmodel.Order;
import business.domainmodel.OrderItem;
import business.pricing.StandardPricing;

/**
 * CV09 Factory Method – the extension from the slide: a new document type added with its own creator,
 * without changing {@link DocumentCreator}'s common logic.
 */
public class Invoice extends OrderDocument {
    public Invoice(Order order) {
        super(order);
    }

    @Override
    public String getTitle() {
        return "Faktura";
    }

    @Override
    public String getContent() {
        Order order = getOrder();
        StringBuilder text = new StringBuilder()
                .append(orderReference()).append('\n')
                .append("Odběratel: ").append(customerName()).append('\n')
                .append("Položky:\n");
        for (OrderItem item : order.getItems()) {
            text.append("  ").append(item.getLineNumber()).append(". ").append(item.getMaterialId()).append(": ")
                    .append(item.getUnitPrice()).append(" × ").append(item.getQuantity()).append(" ks = ")
                    .append(item.totalPrice()).append('\n');
        }
        return text.append("Celkem k úhradě: ").append(new StandardPricing().calculate(order)).append('\n')
                .toString();
    }
}
