package cz.vsb.catering.business.e2e;

import static org.junit.jupiter.api.Assertions.*;

import cz.vsb.catering.business.domain.ConflictException;
import cz.vsb.catering.business.domain.MenuRevision;
import cz.vsb.catering.business.domain.Money;
import cz.vsb.catering.business.domain.RevisionComparison;
import cz.vsb.catering.business.domain.RevisionComparison.Row;
import cz.vsb.catering.business.domain.RevisionState;
import cz.vsb.catering.business.domain.ValidationException;
import cz.vsb.catering.business.pricing.PriceStrategy;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

/** UC09 and UC10 on the seed menu 1 (read only). Seed rules (TDS/db/02_seed.sql), 120 guests:
 *  every next revision raises the cheapest starter (Caprese) by 10 % with note "Navýšeno" (120 -> 132 -> 145),
 *  adds the risotto (24 x 180 Kč) to the main course and drops the last dessert (Čokoládový fondán). */
class Uc09Uc10RevisionHistoryTest extends E2eBaseTest {
    private static final long MENU = 1;

    @Test
    void uc09OlderRevisionsAreShownReadOnly() {
        assertEquals(List.of(3, 2, 1), menus.listRevisions(MENU).stream().map(MenuRevision::getRevisionNo).toList());
        MenuRevision first = menus.openRevision(MENU, 1);
        assertEquals(RevisionState.WORKING, first.getState());               // the seed has an older WORKING revision
        assertTrue(first.isHistorical());
        assertFalse(first.isEditable());
        assertThrows(ValidationException.class, () -> first.addCategory("Polévky"));
        assertEquals(4, first.getCategories().size());
        assertFalse(menus.openRevision(MENU, 2).isEditable());
        assertTrue(menus.openRevision(MENU, 3).isEditable());
        assertThrows(ConflictException.class, () -> menus.createWorkingCopy(first));   // A-05: the newest revision is WORKING
        assertEquals(3, count("SELECT COUNT(*) FROM menu_revisions WHERE menu_id = ?", MENU));
    }

    @Test
    void uc10CompareFirstAndThirdSeedRevision() {
        RevisionComparison diff = menus.compareRevisions(MENU, 1, 3);
        assertEquals(11, diff.getRows().size());
        List<String> changes = diff.changes().stream()
                .map(r -> r.status() + " " + r.category() + "/" + r.product().getName()).toList();
        assertEquals(List.of("CHANGED Předkrmy/Caprese", "ADDED Hlavní chod/Rizoto s hříbky",
                "REMOVED Dezerty/Čokoládový fondán"), changes);
        Row caprese = diff.changes().get(0);
        assertEquals(List.of("množství", "poznámka"), caprese.changes());
        assertEquals(0, new BigDecimal("120").compareTo(caprese.left().getQuantity()));
        assertEquals(0, new BigDecimal("145").compareTo(caprese.right().getQuantity()));
        assertEquals("Navýšeno", caprese.right().getNote());
        // 25 x 70 + 24 x 180 - 120 x 75 = 1750 + 4320 - 9000
        assertEquals(Money.of(-2930), diff.difference(PriceStrategy.WITHOUT_VAT));
        assertEquals(diff.newerTotal(PriceStrategy.WITH_VAT).minus(diff.olderTotal(PriceStrategy.WITH_VAT)),
                diff.difference(PriceStrategy.WITH_VAT));
    }

    @Test
    void uc10SameRevisionHasNoChanges() {
        RevisionComparison diff = menus.compareRevisions(MENU, 2, 2);
        assertTrue(diff.changes().isEmpty());
        assertEquals(Money.ZERO, diff.difference(PriceStrategy.WITH_VAT));
    }
}
