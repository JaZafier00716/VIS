package cz.vsb.catering.business.e2e;

import static org.junit.jupiter.api.Assertions.*;

import cz.vsb.catering.business.domain.Allergen;
import cz.vsb.catering.business.domain.Money;
import cz.vsb.catering.business.domain.Product;
import cz.vsb.catering.business.domain.ValidationException;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** UC06 Spravovat katalog produktů «CRUD»: product with allergens and photo BLOB, price change journaled
 *  by the trigger, unique name and the refused delete of a product used in a menu; checked with JDBC. */
class Uc06CatalogTest extends E2eBaseTest {
    private static final BigDecimal VAT = new BigDecimal("12");
    private final List<Long> created = new ArrayList<>();

    @AfterEach
    void deleteProducts() {
        created.forEach(id -> exec("DELETE FROM products WHERE product_id = ?", id));   // allergens, history cascade
    }

    @Test
    void createUpdateDeleteWithAllergensPhotoAndPriceHistory() throws Exception {
        List<Allergen> all = catalog.findAllergens();
        assertEquals(14, all.size());
        byte[] photo = new byte[80 * 1024];                                             // > 32 kB: real LOB binding
        new Random(6).nextBytes(photo);
        Product p = catalog.create("E2E Tatarák", "z hovězí svíčkové", "portion", new BigDecimal("219.90"), VAT,
                List.of(all.get(6), all.get(0)), photo);
        created.add(p.getId());
        Map<String, Object> row = row("SELECT name, unit, current_price, vat_rate FROM products WHERE product_id = ?", p.getId());
        assertEquals("E2E Tatarák", row.get("NAME"));
        assertEquals("portion", row.get("UNIT"));
        assertEquals(219.90, num(row.get("CURRENT_PRICE")), 0.001);
        assertEquals(List.of(1L, 7L), allergenIds(p.getId()));
        assertArrayEquals(photo, storedPhoto(p.getId()));
        assertArrayEquals(photo, catalog.loadPhoto(p.getId()));
        Product listed = catalog.findProducts().stream().filter(x -> x.getId().equals(p.getId())).findFirst().orElseThrow();
        assertEquals(List.of(all.get(0).name(), all.get(6).name()), listed.getAllergens());

        catalog.update(p.getId(), "E2E Tatarák", null, "portion", new BigDecimal("249"), new BigDecimal("21"),
                List.of(all.get(2)), null);                                             // null photo = keep
        assertEquals(List.of(3L), allergenIds(p.getId()));
        assertArrayEquals(photo, storedPhoto(p.getId()));
        assertEquals(1, count("SELECT COUNT(*) FROM product_price_history WHERE product_id = ? AND old_price = 219.9 "
                + "AND new_price = 249", p.getId()), "trigger journaled the price change");
        assertEquals(List.of(Money.of("219.90")), catalog.findPriceHistory(p.getId()).stream().map(h -> h.oldPrice()).toList());

        byte[] small = {1, 2, 3};
        catalog.update(p.getId(), "E2E Tatarák", null, "portion", new BigDecimal("249"), new BigDecimal("21"), List.of(), small);
        assertArrayEquals(small, storedPhoto(p.getId()));
        assertEquals(List.of(), allergenIds(p.getId()));
        assertNull(catalog.loadPhoto(count("SELECT MIN(product_id) FROM products WHERE photo IS NULL")));
        assertEquals(1, count("SELECT COUNT(*) FROM product_price_history WHERE product_id = ?", p.getId()), "same price, no journal");

        catalog.delete(p.getId());
        assertEquals(0, count("SELECT COUNT(*) FROM products WHERE product_id = ?", p.getId()));
        assertEquals(0, count("SELECT COUNT(*) FROM product_price_history WHERE product_id = ?", p.getId()));
    }

    @Test
    void duplicateNameAndInvalidDataAreRefused() {
        String seedName = (String) row("SELECT name FROM products WHERE product_id = 1").get("NAME");
        ValidationException e = assertThrows(ValidationException.class,
                () -> catalog.create(seedName, null, "pcs", BigDecimal.ONE, VAT, List.of(), null));
        assertTrue(e.getFieldErrors().containsKey("name"));
        e = assertThrows(ValidationException.class, () -> catalog.create("E2E velká fotka", null, "pcs", BigDecimal.ONE,
                VAT, List.of(), new byte[100 * 1024 + 1]));
        assertTrue(e.getFieldErrors().containsKey("photo"));
        assertEquals(0, count("SELECT COUNT(*) FROM products WHERE name LIKE 'E2E%'"));
    }

    @Test
    void productUsedInMenuIsNotDeleted() {
        long id = count("SELECT MIN(product_id) FROM menu_items");
        ValidationException e = assertThrows(ValidationException.class, () -> catalog.delete(id));
        assertTrue(e.getMessage().contains("použit v menu"), e.getMessage());
        assertEquals(1, count("SELECT COUNT(*) FROM products WHERE product_id = ?", id));
    }

    private static List<Long> allergenIds(long productId) {
        return rows("SELECT allergen_id FROM product_allergens WHERE product_id = ? ORDER BY allergen_id", productId)
                .stream().map(r -> (Long) r.get("ALLERGEN_ID")).toList();
    }

    private static byte[] storedPhoto(long productId) throws Exception {
        try (Connection c = connection(); PreparedStatement ps = c.prepareStatement("SELECT photo FROM products WHERE product_id = ?")) {
            ps.setLong(1, productId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getBytes(1);
            }
        }
    }
}
