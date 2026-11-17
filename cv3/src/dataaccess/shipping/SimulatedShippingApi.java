package dataaccess.shipping;

import java.util.Map;

/**
 * CV08 (cv08 p.12): stands in for the external shipping company's API. It speaks the external format – request
 * {@code {"item_count": n}}, response {@code {"cost_czk": …, "eta_days": …}} – and owns the carrier's price rules:
 * 1–3 pieces 120 Kč / 2 days, 4 and more 250 Kč / 4 days. No real network call (not required by the slides).
 */
public class SimulatedShippingApi {
    public Map<String, Object> calculate(Map<String, Object> request) {
        Object count = request.get("item_count");
        if (!(count instanceof Integer pieces) || pieces <= 0) {
            return Map.of("error", "item_count must be a positive integer");
        }
        return pieces <= 3 ? Map.of("cost_czk", 120, "eta_days", 2) : Map.of("cost_czk", 250, "eta_days", 4);
    }
}
