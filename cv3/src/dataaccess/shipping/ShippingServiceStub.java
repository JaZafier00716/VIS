package dataaccess.shipping;

import business.domainmodel.Money;
import business.domainmodel.ShippingQuote;
import business.service.IShippingService;

/** CV08 Service Stub (cv08 p.13–14): replaces the external service with a fixed answer, 150 Kč and 3 days. */
public class ShippingServiceStub implements IShippingService {
    @Override
    public ShippingQuote quote(int pieces) {
        return new ShippingQuote(Money.czk(150), 3);
    }
}
