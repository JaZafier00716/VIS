package configuration;

import java.util.Locale;

/** Which storage the application runs on; chosen by {@code -Dvis.storage=json|h2} or {@code --storage=...}. */
public enum StorageType {
    JSON,
    H2;

    public static StorageType parse(String value) {
        if (value == null || value.isBlank()) {
            return JSON;
        }
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Neznámé úložiště '" + value + "', použijte json nebo h2.");
        }
    }

    public static StorageType fromSystemProperty() {
        return parse(System.getProperty("vis.storage"));
    }
}
