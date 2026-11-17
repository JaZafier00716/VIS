package dataaccess.shipping;

import business.domainmodel.Money;
import business.domainmodel.ShippingQuote;

import java.math.BigDecimal;
import java.util.Map;

/**
 * CV08 Mapper (cv08 p.15–16): translates between the application's model and the external shipping API's data
 * (snake_case keys, plain numbers). Not a Data Mapper: it maps to an external service, not to the database.
 */
public final class ShippingMapper {
    private ShippingMapper() {}

    public static Map<String, Object> toRequest(int pieces) {
        return Map.of("item_count", pieces);
    }

    public static ShippingQuote toQuote(Map<String, Object> response) {
        if (response.containsKey("error")) {
            throw new IllegalStateException("Dopravní služba odmítla požadavek: " + response.get("error"));
        }
        Object cost = response.get("cost_czk");
        Object eta = response.get("eta_days");
        if (!(cost instanceof Number price) || !(eta instanceof Number days)) {
            throw new IllegalStateException("Neplatná odpověď dopravní služby: " + response);
        }
        try {
            return new ShippingQuote(Money.czk(new BigDecimal(price.toString())), days.intValue());
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("Neplatná odpověď dopravní služby: " + response, e);
        }
    }
}
