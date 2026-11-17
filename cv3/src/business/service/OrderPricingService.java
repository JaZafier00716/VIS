package business.service;

import business.domainmodel.DiscountPolicy;
import business.domainmodel.Money;
import business.domainmodel.NoDiscount;
import business.domainmodel.Order;
import business.domainmodel.OrderItem;
import business.domainmodel.ShippingQuote;
import business.pricing.IPricingStrategy;
import business.pricing.StandardPricing;

/**
 * CV08 price calculation (cv08 p.3, p.17): items (Money) → discount (DiscountPolicy, NoDiscount included) →
 * shipping (IShippingService) → total. The calculation is the same whether the shipping service is the stub or
 * the gateway; only the composition root decides which one is used. It only reads the order.
 * CV09: the price of the items comes from a pricing strategy ({@link IPricingStrategy}).
 */
public class OrderPricingService {
    private final OrderService orders;
    private final IShippingService shipping;

    public OrderPricingService(OrderService orders, IShippingService shipping) {
        this.orders = orders;
        this.shipping = shipping;
    }

    public OrderPriceSummary calculate(String orderId, DiscountPolicy discount) {
        DiscountPolicy policy = discount == null ? NoDiscount.INSTANCE : discount;
        Order order = orders.findOrder(orderId);
        // CV09: StandardPricing has no discount, so the DiscountPolicy is never stacked with CompanyPricing.
        Money itemsTotal = new StandardPricing().calculate(order);
        int pieces = order.getItems().stream().mapToInt(OrderItem::getQuantity).sum();
        Money afterDiscount = policy.apply(itemsTotal);
        ShippingQuote quote = shipping.quote(pieces);
        return new OrderPriceSummary(orderId, pieces, itemsTotal, policy, afterDiscount, quote,
                afterDiscount.plus(quote.price()));
    }

    /** CV09 Strategy: the price of the order's items, computed by the strategy the caller chose. */
    public Money calculatePrice(String orderId, IPricingStrategy strategy) {
        if (strategy == null) {
            throw new IllegalArgumentException("Cenová strategie je povinná.");
        }
        return strategy.calculate(orders.findOrder(orderId));
    }
}
