package business.service;

import business.domainmodel.DiscountPolicy;
import business.domainmodel.Money;
import business.domainmodel.ShippingQuote;

/** CV08 (cv08 p.3, p.17): the recapitulation shown to the customer. */
public record OrderPriceSummary(String orderId, int pieces, Money itemsTotal, DiscountPolicy discount,
                                Money afterDiscount, ShippingQuote shipping, Money total) {}
