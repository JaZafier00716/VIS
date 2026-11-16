package cz.vsb.catering;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Properties;
import org.junit.jupiter.api.Test;

/** A-55: web.embedded defaults to false; only the text "true" (any case, trimmed) turns it on. */
class AppContextTest {
    private static Properties with(String value) {
        Properties p = new Properties();
        if (value != null) p.setProperty("web.embedded", value);
        return p;
    }

    @Test void flagParsing() {
        assertFalse(AppContext.webEmbedded(with(null)));
        assertFalse(AppContext.webEmbedded(with("false")));
        assertFalse(AppContext.webEmbedded(with("ano")));
        assertTrue(AppContext.webEmbedded(with("true")));
        assertTrue(AppContext.webEmbedded(with(" TRUE ")));
    }
}
