package cz.vsb.catering.business.domain;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ProductTest {
    private static final Allergen GLUTEN = new Allergen(1, "Lepek"), MILK = new Allergen(7, "Mléko");

    @Test void createValidProduct() {
        Product p = Product.create(" Chlebíček ", "", "pcs", new BigDecimal("35.5"), new BigDecimal("12"),
                List.of(MILK, GLUTEN, MILK), new byte[]{1, 2});
        assertEquals("Chlebíček", p.getName());
        assertNull(p.getDescription());
        assertEquals(Money.of("35.50"), p.getCurrentPrice());
        assertEquals(List.of(GLUTEN, MILK), p.getAllergenList(), "sorted, no duplicates");
        assertEquals(List.of("Lepek", "Mléko"), p.getAllergens());
        assertEquals(2, p.getPhoto().length);
    }

    @Test void invalidFieldsAreReportedTogether() {
        ValidationException e = assertThrows(ValidationException.class, () -> Product.create(" ", "x".repeat(501), "ks",
                new BigDecimal("-1"), new BigDecimal("15"), List.of(), new byte[Product.MAX_PHOTO_BYTES + 1]));
        assertEquals(Set.of("name", "description", "unit", "price", "vatRate", "photo"), e.getFieldErrors().keySet());
    }

    @Test void limits() {
        assertDoesNotThrow(() -> Product.create("x".repeat(100), null, "set", BigDecimal.ZERO, new BigDecimal("21.00"),
                List.of(), new byte[Product.MAX_PHOTO_BYTES]));
        assertThrows(ValidationException.class, () -> Product.create("x".repeat(101), null, "set", BigDecimal.ZERO,
                BigDecimal.ZERO, List.of(), null));
        assertThrows(ValidationException.class, () -> Product.create("A", null, "kg", null, BigDecimal.ZERO, List.of(), null));
    }

    @Test void priceLimits() {
        assertDoesNotThrow(() -> Product.create("A", null, "kg", new BigDecimal("99999999.99"), BigDecimal.ZERO, List.of(), null));
        assertDoesNotThrow(() -> Product.create("A", null, "kg", new BigDecimal("1.500"), BigDecimal.ZERO, List.of(), null));
        assertThrows(ValidationException.class, () -> Product.create("A", null, "kg", new BigDecimal("100000000"), BigDecimal.ZERO, List.of(), null));
        assertThrows(ValidationException.class, () -> Product.create("A", null, "kg", new BigDecimal("1.005"), BigDecimal.ZERO, List.of(), null));
    }
}
