package business.pricing;

import business.domainmodel.Money;
import business.domainmodel.Order;

/**
 * CV09 Strategy: one interchangeable way of computing the price of an order's items. The caller picks the
 * strategy; the code that uses it never asks which one it got.
 */
public interface IPricingStrategy {
    /** Czech name for the price summary, e.g. "Standardní cena". */
    String getName();

    /** The price of all items of the order; an order without items cannot be priced. */
    Money calculate(Order order);
}
