package cz.vsb.catering.business.domain;

import static cz.vsb.catering.business.fake.Fixtures.product;
import static org.junit.jupiter.api.Assertions.*;

import cz.vsb.catering.business.fake.Fixtures;
import cz.vsb.catering.business.pricing.PriceStrategy;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class MenuRevisionTest {
    private final Product bruschetta = product(1, "Bruschetta", "100", "12");
    private final Product wine = product(2, "Víno", "25.50", "21");

    private MenuRevision working() { return MenuRevision.first(5); }

    /** Předkrmy: 10 x 100 Kč (12 %), Nápoje: 3 x 25,50 Kč (21 %). */
    private MenuRevision withTwoCategories() {
        MenuRevision r = working();
        r.addItem(r.addCategory("Předkrmy"), bruschetta, BigDecimal.TEN, Money.of(100), null);
        r.addItem(r.addCategory("Nápoje"), wine, new BigDecimal("3"), Money.of("25.50"), "suché");
        return r;
    }

    @Test void firstRevisionIsEmptyUnsavedWorking() {
        MenuRevision r = working();
        assertEquals(1, r.getRevisionNo());
        assertEquals(RevisionState.WORKING, r.getState());
        assertTrue(r.isEditable());
        assertFalse(r.isStored());
        assertTrue(r.getCategories().isEmpty());
    }

    @Test void addCategoryValidatesName() {
        MenuRevision r = working();
        assertEquals("Předkrmy", r.addCategory(" Předkrmy ").getName());
        assertThrows(ValidationException.class, () -> r.addCategory(" "));
        assertThrows(ValidationException.class, () -> r.addCategory(null));
        assertThrows(ValidationException.class, () -> r.addCategory("předkrmy"));
        assertEquals(1, r.getCategories().size());
    }

    @Test void editingItemsAndCategories() {
        MenuRevision r = working();
        MenuCategory c = r.addCategory("A");
        MenuItem item = r.addItem(c, bruschetta, BigDecimal.ONE, Money.of(100), null);
        r.changeItem(item, new BigDecimal("2"), Money.of(90), "bez lepku");
        assertEquals(new BigDecimal("2"), item.getQuantity());
        assertEquals(Money.of(90), item.getUnitPrice());
        assertEquals("bez lepku", item.getNote());
        assertSame(item, c.findItem(1).orElseThrow());
        assertEquals(0, r.removeItem(c, item));
        assertTrue(c.getItems().isEmpty());
        assertEquals(0, r.removeCategory(c));
        assertTrue(r.getCategories().isEmpty());
    }

    @Test void foreignCategoryOrItemIsRejected() {
        MenuRevision r = working();
        MenuCategory foreign = new MenuCategory("Cizí");
        assertThrows(IllegalArgumentException.class, () -> r.addItem(foreign, bruschetta, BigDecimal.ONE, Money.of(1), null));
        assertThrows(IllegalArgumentException.class, () -> r.removeCategory(foreign));
        MenuCategory c = r.addCategory("A");
        assertThrows(IllegalArgumentException.class,
                () -> r.removeItem(c, new MenuItem(bruschetta, BigDecimal.ONE, Money.of(1), null)));
    }

    @Test void totalsDependOnPriceStrategy() {
        MenuRevision r = withTwoCategories();
        assertEquals(Money.of("1076.50"), r.totalPrice(PriceStrategy.WITHOUT_VAT));
        // 1000 * 1.12 + 25.50 * 3 * 1.21 = 1120 + 92.565 -> 1212.57
        assertEquals(Money.of("1212.57"), r.totalPrice(PriceStrategy.WITH_VAT));
        assertEquals(Money.of("1000"), r.getCategories().get(0).subtotal(PriceStrategy.WITHOUT_VAT));
        assertEquals(Money.of("107.65"), r.pricePerGuest(PriceStrategy.WITHOUT_VAT, 10));
        assertEquals(Money.of("121.26"), r.pricePerGuest(PriceStrategy.WITH_VAT, 10));
        assertEquals(Money.ZERO, working().totalPrice(PriceStrategy.WITH_VAT));
        assertEquals("bez DPH", PriceStrategy.WITHOUT_VAT.label());
        assertEquals("s DPH", PriceStrategy.WITH_VAT.label());
    }

    @Test void findProblems() {
        MenuRevision empty = working();
        assertEquals(List.of("Menu musí obsahovat alespoň jednu kategorii."), empty.findProblems());

        MenuRevision r = working();
        MenuCategory c = r.addCategory("A");
        r.addItem(c, bruschetta, BigDecimal.ZERO, Money.of(10), null);
        r.addItem(c, wine, new BigDecimal("-1"), Money.of(-5), null);
        r.addItem(c, bruschetta, BigDecimal.ONE, Money.of(10), null);
        List<String> problems = r.findProblems();
        assertEquals(4, problems.size(), problems.toString());
        assertTrue(problems.get(0).startsWith("A / Bruschetta: množství"));
        assertTrue(problems.get(1).startsWith("A / Víno: množství"));
        assertTrue(problems.get(2).startsWith("A / Víno: cena"));
        assertTrue(problems.get(3).contains("dvakrát"));

        ValidationException e = assertThrows(ValidationException.class, r::validate);
        assertEquals(problems.get(0), e.getFieldErrors().get("menu"));
        assertTrue(withTwoCategories().findProblems().isEmpty());
        assertDoesNotThrow(withTwoCategories()::validate);
    }

    @Test void sameProductInDifferentCategoriesIsAllowed() {
        MenuRevision r = working();
        r.addItem(r.addCategory("A"), bruschetta, BigDecimal.ONE, Money.of(1), null);
        r.addItem(r.addCategory("B"), bruschetta, BigDecimal.ONE, Money.of(1), null);
        assertTrue(r.findProblems().isEmpty());
    }

    @Test void sharedRevisionIsReadOnly() {
        MenuRevision r = withTwoCategories();
        r.markShared();
        assertEquals(RevisionState.SHARED, r.getState());
        assertFalse(r.isEditable());
        MenuCategory c = r.getCategories().get(0);
        MenuItem item = c.getItems().get(0);
        assertThrows(ValidationException.class, () -> r.addCategory("X"));
        assertThrows(ValidationException.class, () -> r.addItem(c, wine, BigDecimal.ONE, Money.of(1), null));
        assertThrows(ValidationException.class, () -> r.changeItem(item, BigDecimal.ONE, Money.of(1), null));
        assertThrows(ValidationException.class, () -> r.removeItem(c, item));
        assertThrows(ValidationException.class, () -> r.removeCategory(c));
        assertThrows(ValidationException.class, () -> r.insertCategory(0, new MenuCategory("Y")));
        assertEquals(BigDecimal.TEN, item.getQuantity());
    }

    @Test void invalidRevisionIsNotShared() {
        MenuRevision r = working();
        assertThrows(ValidationException.class, r::markShared);
        assertEquals(RevisionState.WORKING, r.getState());
    }

    @Test void workingCopyIsDeepCopyWithNextNumber() {
        MenuRevision shared = withTwoCategories();
        shared.markShared();
        MenuRevision copy = shared.createWorkingCopy();
        assertEquals(2, copy.getRevisionNo());
        assertEquals(5, copy.getMenuId());
        assertEquals(RevisionState.WORKING, copy.getState());
        assertFalse(copy.isStored());
        assertEquals(shared.getCategories().size(), copy.getCategories().size());
        assertNotSame(shared.getCategories().get(0), copy.getCategories().get(0));
        assertEquals("Předkrmy", copy.getCategories().get(0).getName());

        MenuItem copied = copy.getCategories().get(0).getItems().get(0);
        assertNotSame(shared.getCategories().get(0).getItems().get(0), copied);
        copy.changeItem(copied, new BigDecimal("99"), Money.of(1), null);
        copy.addCategory("Dezerty");
        assertEquals(BigDecimal.TEN, shared.getCategories().get(0).getItems().get(0).getQuantity());
        assertEquals(2, shared.getCategories().size());
        assertEquals(shared.totalPrice(PriceStrategy.WITHOUT_VAT), Money.of("1076.50"));
    }

    @Test void workingRevisionHasNoWorkingCopy() {
        assertThrows(IllegalStateException.class, () -> working().createWorkingCopy());
    }

    @Test void recordSaveAndMarkStored() {
        MenuRevision r = working();
        r.recordSave("pozn", 3L, Fixtures.NOW);
        r.markStored();
        assertEquals("pozn", r.getNote());
        assertEquals(3L, r.getCreatedBy());
        assertEquals(Fixtures.NOW, r.getCreatedAt());
        assertTrue(r.isStored());
        assertEquals("Revize 1 (PRACOVNÍ)", r.toString());
    }

    // ------------------------------------------------------------ Observer

    @Test void listenersAreNotifiedOnSuccessfulChangeOnly() {
        MenuRevision r = working();
        List<MenuRevision> events = new ArrayList<>();
        MenuRevisionListener listener = events::add;
        r.addListener(listener);

        MenuCategory c = r.addCategory("A");
        MenuItem item = r.addItem(c, bruschetta, BigDecimal.ONE, Money.of(1), null);
        r.changeItem(item, BigDecimal.TEN, Money.of(1), null);
        assertEquals(3, events.size());
        assertSame(r, events.get(0));

        assertThrows(ValidationException.class, () -> r.addCategory("a"));
        assertThrows(IllegalArgumentException.class, () -> r.removeCategory(new MenuCategory("X")));
        assertEquals(3, events.size());

        r.removeListener(listener);
        r.addCategory("B");
        assertEquals(3, events.size());
    }

    @Test void markSharedNotifiesListeners() {
        MenuRevision r = withTwoCategories();
        AtomicInteger count = new AtomicInteger();
        r.addListener(rev -> count.incrementAndGet());
        r.markShared();
        assertEquals(1, count.get());
    }

    // ------------------------------------------------------------ Lazy Load

    @Test void commentsAreLoadedOnceOnFirstAccess() {
        MenuRevision r = working();
        AtomicInteger calls = new AtomicInteger();
        Comment comment = Comment.create(5, 1, null, null, "J", "B", Fixtures.NOW);
        r.setCommentLoader(() -> { calls.incrementAndGet(); return List.of(comment); });
        assertFalse(r.areCommentsLoaded());
        assertEquals(0, calls.get());

        assertEquals(List.of(comment), r.getComments());
        assertEquals(1, r.getComments().size());
        assertTrue(r.areCommentsLoaded());
        assertEquals(1, calls.get());
        assertThrows(UnsupportedOperationException.class, () -> r.getComments().clear());
    }

    @Test void defaultLoaderGivesNoComments() {
        assertTrue(working().getComments().isEmpty());
    }

    // ------------------------------------------------------------ A-09 column limits, A-23 nulls

    private List<String> problemsOfItem(String quantity, String price, String note) {
        MenuRevision r = working();
        r.addItem(r.addCategory("A"), bruschetta, new BigDecimal(quantity), Money.of(price), note);
        return r.findProblems();
    }

    @Test void quantityLimits() {
        assertTrue(problemsOfItem("999999.99", "1", null).isEmpty());
        assertTrue(problemsOfItem("0.01", "1", null).isEmpty());
        assertTrue(problemsOfItem("2.50", "1", null).isEmpty());
        assertEquals(1, problemsOfItem("1000000", "1", null).size());
        assertEquals(1, problemsOfItem("0.001", "1", null).size());
        assertEquals(1, problemsOfItem("0.004", "1", null).size());
        assertEquals(1, problemsOfItem("0", "1", null).size());
    }

    @Test void unitPriceLimit() {
        assertTrue(problemsOfItem("1", "99999999.99", null).isEmpty());
        assertEquals(1, problemsOfItem("1", "100000000", null).size());
    }

    @Test void noteAndCategoryNameLimits() {
        assertTrue(problemsOfItem("1", "1", "x".repeat(200)).isEmpty());
        assertEquals(1, problemsOfItem("1", "1", "x".repeat(201)).size());
        MenuRevision r = working();
        assertDoesNotThrow(() -> r.addCategory("k".repeat(60)));
        assertThrows(ValidationException.class, () -> r.addCategory("k".repeat(61)));
    }

    @Test void nullQuantityOrPriceIsRejected() {
        MenuRevision r = working();
        MenuCategory c = r.addCategory("A");
        assertThrows(ValidationException.class, () -> r.addItem(c, bruschetta, null, Money.of(1), null));
        assertThrows(ValidationException.class, () -> r.addItem(c, bruschetta, BigDecimal.ONE, null, null));
        MenuItem item = r.addItem(c, bruschetta, BigDecimal.ONE, Money.of(1), null);
        assertThrows(ValidationException.class, () -> r.changeItem(item, null, Money.of(1), null));
        assertThrows(ValidationException.class, () -> r.changeItem(item, BigDecimal.ONE, null, null));
        assertEquals(1, c.getItems().size());
    }
}
