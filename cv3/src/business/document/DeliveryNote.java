package business.document;

import business.domainmodel.Order;
import business.domainmodel.OrderItem;

/** CV09 Factory Method – a concrete Product: the delivery note that travels with the goods (no prices). */
public class DeliveryNote extends OrderDocument {
    public DeliveryNote(Order order) {
        super(order);
    }

    @Override
    public String getTitle() {
        return "Dodací list";
    }

    @Override
    public String getContent() {
        Order order = getOrder();
        StringBuilder text = new StringBuilder()
                .append(orderReference()).append('\n')
                .append("Příjemce: ").append(customerName()).append('\n')
                .append("Položky:\n");
        for (OrderItem item : order.getItems()) {
            text.append("  ").append(item.getLineNumber()).append(". ").append(item.getMaterialId())
                    .append(" – ").append(item.getQuantity()).append(" ks\n");
        }
        int pieces = order.getItems().stream().mapToInt(OrderItem::getQuantity).sum();
        return text.append("Celkem kusů: ").append(pieces).append('\n').toString();
    }
}
