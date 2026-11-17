package business.pricing;

import business.domainmodel.Money;
import business.domainmodel.Order;

import java.math.BigDecimal;

/** CV09 Strategy: the price for company customers – the standard price minus 10 % (rounded half up). */
public class CompanyPricing implements IPricingStrategy {
    private static final BigDecimal FACTOR = new BigDecimal("0.90");

    private final StandardPricing standard = new StandardPricing();

    @Override
    public String getName() {
        return "Firemní cena (sleva 10 %)";
    }

    @Override
    public Money calculate(Order order) {
        return standard.calculate(order).times(FACTOR);
    }
}
