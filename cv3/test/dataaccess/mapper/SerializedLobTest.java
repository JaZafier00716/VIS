package dataaccess.mapper;

import business.domainmodel.ProductConfiguration;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** CV06 - Serialized LOB: the whole configuration becomes one versioned JSON text and back. */
class SerializedLobTest {

    @Test
    void roundTripKeepsEveryValue() {
        ProductConfiguration configuration = new ProductConfiguration("bílá", "CNC",
                Map.of("úchytky", "černé", "police", "4"));

        String json = ProductConfigurationSerializer.serialize(configuration);

        assertTrue(json.contains("\"version\":1"));
        assertEquals(configuration, ProductConfigurationSerializer.deserialize(json));
    }

    @Test
    void outputIsDeterministic() {
        ProductConfiguration a = new ProductConfiguration("bílá", "CNC", Map.of("b", "2", "a", "1"));
        ProductConfiguration b = new ProductConfiguration("bílá", "CNC", Map.of("a", "1", "b", "2"));

        assertEquals(ProductConfigurationSerializer.serialize(a), ProductConfigurationSerializer.serialize(b));
    }

    @Test
    void nullStaysNull() {
        assertNull(ProductConfigurationSerializer.serialize(null));
        assertNull(ProductConfigurationSerializer.deserialize(null));
    }

    @Test
    void unknownVersionOrBrokenTextIsRejected() {
        assertThrows(IllegalStateException.class, () -> ProductConfigurationSerializer.deserialize(
                "{\"version\":2,\"color\":\"bílá\",\"machining\":\"CNC\",\"options\":{}}"));
        assertThrows(IllegalStateException.class, () -> ProductConfigurationSerializer.deserialize("{nonsense"));
        assertThrows(IllegalStateException.class, () -> ProductConfigurationSerializer.deserialize(
                "{\"version\":1,\"color\":\"\",\"machining\":\"CNC\",\"options\":{}}"));
    }
}
