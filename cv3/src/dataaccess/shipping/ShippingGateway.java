package dataaccess.shipping;

import business.domainmodel.ShippingQuote;
import business.service.IShippingService;

/**
 * CV08 Gateway (cv08 p.11–12): the only place that talks to the external shipping service. It has no price
 * rules of its own: it sends the request prepared by {@link ShippingMapper} and maps the answer back.
 */
public class ShippingGateway implements IShippingService {
    private final SimulatedShippingApi api;

    public ShippingGateway(SimulatedShippingApi api) {
        this.api = api;
    }

    @Override
    public ShippingQuote quote(int pieces) {
        return ShippingMapper.toQuote(api.calculate(ShippingMapper.toRequest(pieces)));
    }
}
