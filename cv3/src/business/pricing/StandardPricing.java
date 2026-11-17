package business.pricing;

import business.domainmodel.Money;
import business.domainmodel.Order;
import business.domainmodel.OrderItem;

/** CV09 Strategy: the plain price – the sum of all item lines (unit price × quantity), no discount. */
public class StandardPricing implements IPricingStrategy {
    @Override
    public String getName() {
        return "Standardní cena";
    }

    @Override
    public Money calculate(Order order) {
        if (order.getItems().isEmpty()) {
            throw new IllegalArgumentException("Objednávka " + order.getOrderId() + " nemá žádné položky.");
        }
        return order.getItems().stream().map(OrderItem::totalPrice).reduce(Money::plus).orElseThrow();
    }
}
