package cz.vsb.catering.business.domain;

import static org.junit.jupiter.api.Assertions.*;

import java.util.HexFormat;
import org.junit.jupiter.api.Test;

class PasswordHasherTest {
    /** Seed value of user mkral in 02_seed.sql (password heslo123, salt 0101...). */
    static final String SEED_MKRAL = PasswordHasher.hash("heslo123", HexFormat.of().parseHex("01010101010101010101010101010101"), 65_536);

    @Test void hashHasIterationsSaltAndHashAndUsesRandomSalt() {
        String a = PasswordHasher.hash("heslo123");
        String b = PasswordHasher.hash("heslo123");
        assertNotEquals(a, b);
        String[] parts = a.split(":");
        assertEquals(3, parts.length);
        assertEquals("65536", parts[0]);
        assertEquals(32, parts[1].length());
        assertEquals(64, parts[2].length());
    }

    @Test void matchesOnlyTheRightPassword() {
        String stored = PasswordHasher.hash("heslo123");
        assertTrue(PasswordHasher.matches("heslo123", stored));
        assertTrue(PasswordHasher.matches("heslo123", SEED_MKRAL + " "));
        assertFalse(PasswordHasher.matches("heslo124", stored));
        assertFalse(PasswordHasher.matches("heslo123", null));
        assertFalse(PasswordHasher.matches("heslo123", "garbage"));
    }
}
