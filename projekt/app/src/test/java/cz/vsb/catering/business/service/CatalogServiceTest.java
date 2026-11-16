package cz.vsb.catering.business.service;

import static org.junit.jupiter.api.Assertions.*;

import cz.vsb.catering.business.domain.Money;
import cz.vsb.catering.business.domain.Product;
import cz.vsb.catering.business.domain.ValidationException;
import cz.vsb.catering.business.fake.DirectTransactionRunner;
import cz.vsb.catering.business.fake.FakeProductMapper;
import cz.vsb.catering.business.fake.Fixtures;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class CatalogServiceTest {
    private static final BigDecimal VAT = new BigDecimal("12");
    private final FakeProductMapper mapper = new FakeProductMapper(Fixtures.product(1, "Bruschetta", "100", "12"));
    private final CatalogService service = new CatalogService(mapper, new DirectTransactionRunner());
    private final List<cz.vsb.catering.business.domain.Allergen> allergens = service.findAllergens();

    @Test void createWithAllergensAndPhoto() {
        Product p = service.create("Kuřecí řízek", "s bramborem", "portion", new BigDecimal("189"), VAT,
                List.of(allergens.get(0), allergens.get(1)), new byte[]{9});
        assertEquals(2, service.findProducts().size());
        assertEquals(List.of("Lepek", "Vejce"), mapper.stored(p.getId()).getAllergens());
        assertArrayEquals(new byte[]{9}, service.loadPhoto(p.getId()));
    }

    @Test void updateKeepsPhotoWhenNoneGiven() {
        Product p = service.create("Polévka", null, "portion", new BigDecimal("45"), VAT, List.of(), new byte[]{1});
        service.update(p.getId(), "Polévka dne", null, "portion", new BigDecimal("49"), VAT, List.of(allergens.get(2)), null);
        Product stored = mapper.stored(p.getId());
        assertEquals("Polévka dne", stored.getName());
        assertEquals(Money.of(49), stored.getCurrentPrice());
        assertEquals(List.of("Mléko"), stored.getAllergens());
        assertArrayEquals(new byte[]{1}, service.loadPhoto(p.getId()));
    }

    @Test void duplicateNameIsFieldError() {
        ValidationException e = assertThrows(ValidationException.class,
                () -> service.create(" bruschetta ", null, "pcs", BigDecimal.ONE, VAT, List.of(), null));
        assertTrue(e.getFieldErrors().containsKey("name"));
        assertEquals(1, mapper.products.size());
        Product p = service.create("Chlebíček", null, "pcs", BigDecimal.ONE, VAT, List.of(), null);
        assertThrows(ValidationException.class,
                () -> service.update(p.getId(), "Bruschetta", null, "pcs", BigDecimal.ONE, VAT, List.of(), null));
        assertEquals("Chlebíček", mapper.stored(p.getId()).getName());
        service.update(p.getId(), "Chlebíček", "nový popis", "pcs", BigDecimal.ONE, VAT, List.of(), null);   // own name is fine
    }

    @Test void deleteRefusedWhenUsedInMenu() {
        mapper.usedInMenu.add(1L);
        ValidationException e = assertThrows(ValidationException.class, () -> service.delete(1));
        assertTrue(e.getMessage().contains("použit v menu"), e.getMessage());
        assertEquals(1, mapper.products.size());
        Product p = service.create("Limonáda", null, "l", new BigDecimal("60"), new BigDecimal("21"), List.of(), new byte[]{1});
        service.delete(p.getId());
        assertNull(mapper.stored(p.getId()));
        assertNull(service.loadPhoto(p.getId()));
    }

    @Test void invalidDataIsNotStored() {
        assertThrows(ValidationException.class,
                () -> service.create("X", null, "ks", BigDecimal.ONE, VAT, List.of(), null));
        assertThrows(ValidationException.class,
                () -> service.update(1, "Bruschetta", null, "pcs", new BigDecimal("-5"), VAT, List.of(), null));
        assertEquals(Money.of(100), mapper.stored(1).getCurrentPrice());
        assertThrows(ValidationException.class,
                () -> service.update(99, "A", null, "pcs", BigDecimal.ONE, VAT, List.of(), null));
    }
}
