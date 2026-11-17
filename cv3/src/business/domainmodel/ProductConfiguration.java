package business.domainmodel;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;

/**
 * Immutable snapshot of how an order item was configured when it was ordered.
 * Serialized LOB: the data access layer stores it as one serialized value.
 */
@Getter
@EqualsAndHashCode
@ToString
public class ProductConfiguration {
    private final String color;
    private final String machining;
    private final Map<String, String> options;

    public ProductConfiguration(String color, String machining, Map<String, String> options) {
        if (color == null || color.isBlank()) {
            throw new IllegalArgumentException("Konfigurace musí mít barvu.");
        }
        if (machining == null || machining.isBlank()) {
            throw new IllegalArgumentException("Konfigurace musí mít typ opracování.");
        }
        Map<String, String> copy = new TreeMap<>(options == null ? Map.of() : options);
        if (copy.keySet().stream().anyMatch(key -> key == null || key.isBlank())
                || copy.values().stream().anyMatch(value -> value == null)) {
            throw new IllegalArgumentException("Volitelné parametry musí mít název a hodnotu.");
        }
        this.color = color;
        this.machining = machining;
        this.options = Collections.unmodifiableMap(copy);
    }
}
