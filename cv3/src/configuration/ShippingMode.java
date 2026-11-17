package configuration;

import java.util.Locale;

/**
 * CV08 (cv08 p.14): which shipping service the composition root wires, chosen by {@code --shipping=stub|gateway}
 * or {@code -Dvis.shipping=stub|gateway} (default GATEWAY). The business logic is the same in both modes.
 */
public enum ShippingMode {
    /** ShippingServiceStub: fixed answer, no external communication. */
    STUB,
    /** ShippingGateway: communication with the simulated external API through ShippingMapper. */
    GATEWAY;

    public static ShippingMode parse(String value) {
        if (value == null || value.isBlank()) {
            return GATEWAY;
        }
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Neznámý režim dopravy '" + value + "', použijte stub nebo gateway.");
        }
    }

    public static ShippingMode fromSystemProperty() {
        return parse(System.getProperty("vis.shipping"));
    }
}
