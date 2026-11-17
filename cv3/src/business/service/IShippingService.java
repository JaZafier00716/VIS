package business.service;

import business.domainmodel.ShippingQuote;

/**
 * CV08 (cv08 p.4, p.12, p.14): the contract through which the business layer gets the shipping price and the
 * delivery time. The business layer owns the interface; the data access layer implements it with
 * {@code ShippingGateway} (simulated external API) or {@code ShippingServiceStub} (fixed answer). The name keeps
 * the slides' {@code IShippingService}.
 */
public interface IShippingService {
    ShippingQuote quote(int pieces);
}
