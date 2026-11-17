package business.document;

import business.domainmodel.Order;
import business.domainmodel.OrderSpecification;

/** CV09 Factory Method – a concrete Product: the order confirmation sent to the customer. */
public class OrderConfirmation extends OrderDocument {
    public OrderConfirmation(Order order) {
        super(order);
    }

    @Override
    public String getTitle() {
        return "Potvrzení objednávky";
    }

    @Override
    public String getContent() {
        Order order = getOrder();
        OrderSpecification spec = order.getSpecification();
        StringBuilder text = new StringBuilder()
                .append(orderReference()).append('\n')
                .append("Zákazník: ").append(customerName()).append('\n')
                .append("Vytvořena: ").append(createdDate()).append('\n')
                .append("Stav: ").append(order.getStatus().getLabel()).append('\n')
                .append("Materiál: ").append(order.getMaterialId()).append('\n');
        if (spec != null) {
            text.append("Rozměry (š × v × h): ").append(spec.getWidth()).append(" × ").append(spec.getHeight())
                    .append(" × ").append(spec.getDepth()).append('\n');
            if (spec.getDesignNotes() != null && !spec.getDesignNotes().isBlank()) {
                text.append("Poznámka k návrhu: ").append(spec.getDesignNotes()).append('\n');
            }
        }
        return text.append("Počet položek: ").append(order.getItems().size()).append('\n').toString();
    }
}
