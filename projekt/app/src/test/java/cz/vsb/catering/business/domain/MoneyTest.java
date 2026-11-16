package cz.vsb.catering.business.domain;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class MoneyTest {
    @Test void equalByValueRegardlessOfScale() {
        assertEquals(Money.of("10"), Money.of("10.00"));
        assertEquals(Money.of("10").hashCode(), Money.of(new BigDecimal("10.000")).hashCode());
        assertNotEquals(Money.of("10.01"), Money.of("10"));
    }

    @Test void roundsHalfUpToTwoDecimals() {
        assertEquals(new BigDecimal("1.01"), Money.of("1.005").amount());
        assertEquals(new BigDecimal("2.34"), Money.of("2.344").amount());
    }

    @Test void formatsCzech() {
        assertEquals("1 234,50 Kč", Money.of("1234.5").format());
        assertEquals("0,00 Kč", Money.ZERO.format());
        assertEquals("1 000 000,00 Kč", Money.of(1_000_000).toString());
    }

    @Test void arithmetic() {
        assertEquals(Money.of("15.50"), Money.of("10.25").plus(Money.of("5.25")));
        assertEquals(Money.of("25.50"), Money.of("8.50").times(new BigDecimal("3")));
        assertEquals(Money.of("3.33"), Money.of(10).dividedBy(3));
        assertThrows(IllegalArgumentException.class, () -> Money.of(10).dividedBy(0));
    }

    @Test void negativeComparisonAndNull() {
        assertTrue(Money.of("-0.01").isNegative());
        assertFalse(Money.ZERO.isNegative());
        assertTrue(Money.of(1).compareTo(Money.of(2)) < 0);
        assertThrows(IllegalArgumentException.class, () -> Money.of((BigDecimal) null));
    }
}
