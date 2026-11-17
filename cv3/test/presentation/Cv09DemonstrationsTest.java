package presentation;

import configuration.CustomerMapping;
import configuration.StorageType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import support.IsolatedJsonStorage;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** CV09: the demonstration runs all scenarios of the slide without an unexpected error. */
class Cv09DemonstrationsTest {
    @TempDir
    Path dir;

    @Test
    void demonstrationShowsAllPatterns() {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try (IsolatedJsonStorage ignored = new IsolatedJsonStorage(dir);
             PrintStream out = new PrintStream(buffer, true, StandardCharsets.UTF_8)) {
            new Cv09Demonstrations(out, StorageType.JSON, CustomerMapping.CTI, dir).runAll();
        }
        String output = buffer.toString(StandardCharsets.UTF_8);

        assertTrue(output.contains("stejná instance: true"), output);
        assertTrue(output.contains("Standardní cena: 2000.00 CZK"), output);
        assertTrue(output.contains("Firemní cena (sleva 10 %): 1800.00 CZK"), output);
        assertTrue(output.contains("potvrzena – připravit výrobu"), output);
        assertTrue(output.contains("připravena k expedici"), output);
        assertTrue(output.contains("Potvrzení objednávky"), output);
        assertTrue(output.contains("Dodací list"), output);
        assertTrue(output.contains("Faktura"), output);
        assertTrue(output.contains("odmítnuto: "), output);
        assertTrue(output.contains("stav zůstal: Nová"), output);
        assertFalse(output.contains("Neočekávaná chyba"), output);
        // After unregistering, only the first confirmation reached production.
        assertTrue(output.indexOf("potvrzena – připravit výrobu")
                == output.lastIndexOf("potvrzena – připravit výrobu"), output);
    }
}
