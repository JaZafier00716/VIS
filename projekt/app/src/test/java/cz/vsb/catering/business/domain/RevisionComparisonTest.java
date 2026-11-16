package cz.vsb.catering.business.domain;

import static cz.vsb.catering.business.fake.Fixtures.product;
import static org.junit.jupiter.api.Assertions.*;

import cz.vsb.catering.business.domain.RevisionComparison.Row;
import cz.vsb.catering.business.domain.RevisionComparison.Status;
import cz.vsb.catering.business.pricing.PriceStrategy;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

/** UC10: every row status, a product moved between categories, empty revisions and totals. */
class RevisionComparisonTest {
    private final Product bruschetta = product(1, "Bruschetta", "100", "12");
    private final Product caprese = product(2, "Caprese", "70", "12");
    private final Product risotto = product(9, "Rizoto", "180", "12");
    private final Product cake = product(14, "Koláčky", "12", "12");


    private static String key(Row r) { return r.status() + " " + r.category() + "/" + r.product().getName(); }

    @Test void findsAddedRemovedChangedAndUnchangedRows() {
        MenuRevision a = MenuRevision.first(1);
        MenuCategory starters = a.addCategory("Předkrmy");
        a.addItem(starters, bruschetta, new BigDecimal("10"), Money.of(100), null);
        a.addItem(starters, caprese, new BigDecimal("40"), Money.of(70), null);
        MenuCategory desserts = a.addCategory("Dezerty");
        a.addItem(desserts, cake, new BigDecimal("50"), Money.of(12), null);

        MenuRevision b = MenuRevision.first(1);
        starters = b.addCategory("Předkrmy");
        b.addItem(starters, bruschetta, new BigDecimal("10.0"), Money.of(100), null);          // same (scale ignored)
        b.addItem(starters, caprese, new BigDecimal("30"), Money.of(75), "menší porce");       // all three changed
        MenuCategory mains = b.addCategory("Hlavní chod");
        b.addItem(mains, risotto, new BigDecimal("5"), Money.of(180), null);

        RevisionComparison diff = new RevisionComparison(a, b);
        assertEquals(List.of("UNCHANGED Předkrmy/Bruschetta", "CHANGED Předkrmy/Caprese", "REMOVED Dezerty/Koláčky",
                "ADDED Hlavní chod/Rizoto"), diff.getRows().stream().map(RevisionComparisonTest::key).toList());
        Row changed = diff.getRows().get(1);
        assertEquals(List.of("množství", "jedn. cena", "poznámka"), changed.changes());
        assertEquals(new BigDecimal("40"), changed.left().getQuantity());
        assertEquals(new BigDecimal("30"), changed.right().getQuantity());
        assertNull(diff.getRows().get(2).right());
        assertNull(diff.getRows().get(3).left());
        assertEquals(3, diff.changes().size());

        // 1000 + 2800 + 600 = 4400 -> 1000 + 2250 + 900 = 4150
        assertEquals(Money.of(4400), diff.olderTotal(PriceStrategy.WITHOUT_VAT));
        assertEquals(Money.of(4150), diff.newerTotal(PriceStrategy.WITHOUT_VAT));
        assertEquals(Money.of(-250), diff.difference(PriceStrategy.WITHOUT_VAT));
        assertEquals(Money.of(-280), diff.difference(PriceStrategy.WITH_VAT));
    }

    @Test void productMovedToAnotherCategoryIsRemovedThereAndAddedHere() {
        MenuRevision a = MenuRevision.first(1);
        a.addItem(a.addCategory("Předkrmy"), caprese, BigDecimal.TEN, Money.of(70), null);
        a.addCategory("Hlavní chod");
        MenuRevision b = MenuRevision.first(1);
        b.addCategory("Předkrmy");
        b.addItem(b.addCategory("Hlavní chod"), caprese, BigDecimal.TEN, Money.of(70), null);

        RevisionComparison diff = new RevisionComparison(a, b);
        assertEquals(List.of("REMOVED Předkrmy/Caprese", "ADDED Hlavní chod/Caprese"),
                diff.getRows().stream().map(RevisionComparisonTest::key).toList());
        assertEquals(Money.ZERO, diff.difference(PriceStrategy.WITH_VAT));
    }

    @Test void emptyRevisionAgainstFilledOne() {
        MenuRevision empty = MenuRevision.first(1);
        MenuRevision filled = MenuRevision.first(1);
        filled.addItem(filled.addCategory("Předkrmy"), bruschetta, BigDecimal.ONE, Money.of(100), null);

        assertEquals(List.of(Status.ADDED), new RevisionComparison(empty, filled).getRows().stream().map(Row::status).toList());
        assertEquals(List.of(Status.REMOVED), new RevisionComparison(filled, empty).getRows().stream().map(Row::status).toList());
        RevisionComparison none = new RevisionComparison(empty, MenuRevision.first(1));
        assertTrue(none.getRows().isEmpty());
        assertEquals(Money.ZERO, none.difference(PriceStrategy.WITH_VAT));
        assertEquals(Money.of(100), new RevisionComparison(empty, filled).difference(PriceStrategy.WITHOUT_VAT));
    }
}
