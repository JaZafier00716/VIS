package dataaccess.mapper;

import business.domainmodel.ProductConfiguration;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.util.Map;

/**
 * Serialized LOB: converts a {@link ProductConfiguration} to one JSON string and back.
 * The concrete target type is fixed (no polymorphic typing) and the format carries a version.
 */
public final class ProductConfigurationSerializer {
    static final int FORMAT_VERSION = 1;
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);

    private ProductConfigurationSerializer() {}

    private record ConfigurationLob(int version, String color, String machining, Map<String, String> options) {}

    public static String serialize(ProductConfiguration configuration) {
        if (configuration == null) {
            return null;
        }
        ConfigurationLob lob = new ConfigurationLob(FORMAT_VERSION, configuration.getColor(),
                configuration.getMachining(), configuration.getOptions());
        try {
            return MAPPER.writeValueAsString(lob);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Konfiguraci nelze serializovat.", e);
        }
    }

    public static ProductConfiguration deserialize(String json) {
        if (json == null) {
            return null;
        }
        ConfigurationLob lob;
        try {
            lob = MAPPER.readValue(json, ConfigurationLob.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Uložená konfigurace není platný JSON ve verzi " + FORMAT_VERSION + ".", e);
        }
        if (lob == null || lob.version() != FORMAT_VERSION) {
            throw new IllegalStateException("Nepodporovaná verze uložené konfigurace: "
                    + (lob == null ? "null" : lob.version()));
        }
        try {
            return new ProductConfiguration(lob.color(), lob.machining(), lob.options());
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("Uložená konfigurace je neúplná: " + e.getMessage(), e);
        }
    }
}
