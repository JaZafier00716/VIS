package cz.vsb.catering.business.command;

import static cz.vsb.catering.business.fake.Fixtures.product;
import static org.junit.jupiter.api.Assertions.*;

import cz.vsb.catering.business.domain.MenuCategory;
import cz.vsb.catering.business.domain.MenuItem;
import cz.vsb.catering.business.domain.MenuRevision;
import cz.vsb.catering.business.domain.Money;
import cz.vsb.catering.business.domain.Product;
import cz.vsb.catering.business.domain.ValidationException;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class MenuEditorTest {
    private final Product bruschetta = product(1, "Bruschetta", "100", "12");
    private final Product wine = product(2, "Víno", "25", "21");
    private final MenuRevision revision = MenuRevision.first(5);
    private final MenuEditor editor = new MenuEditor(revision);

    private MenuCategory addCategory(String name) {
        AddCategoryCommand cmd = new AddCategoryCommand(revision, name);
        editor.execute(cmd);
        return cmd.getCreated();
    }

    @Test void addCategoryExecuteAndUndo() {
        MenuCategory c = addCategory("Předkrmy");
        assertEquals(List.of(c), revision.getCategories());
        assertTrue(editor.canUndo());
        assertEquals("Přidat kategorii Předkrmy", editor.undo());
        assertTrue(revision.getCategories().isEmpty());
        assertFalse(editor.canUndo());
        assertEquals(List.of("Přidat kategorii Předkrmy", "Zpět: Přidat kategorii Předkrmy"), editor.getLog());
        assertSame(revision, editor.getRevision());
    }

    @Test void removeCategoryUndoRestoresPosition() {
        MenuCategory a = addCategory("A");
        MenuCategory b = addCategory("B");
        MenuCategory c = addCategory("C");
        editor.execute(new RemoveCategoryCommand(revision, b));
        assertEquals(List.of(a, c), revision.getCategories());
        editor.undo();
        assertEquals(List.of(a, b, c), revision.getCategories());
    }

    @Test void addItemExecuteAndUndo() {
        MenuCategory c = addCategory("A");
        AddItemCommand cmd = new AddItemCommand(revision, c, bruschetta, new BigDecimal("50"), Money.of(100));
        editor.execute(cmd);
        MenuItem item = cmd.getCreated();
        assertEquals(List.of(item), c.getItems());
        assertEquals(new BigDecimal("50"), item.getQuantity());
        assertEquals("Přidat Bruschetta do A", editor.undo());
        assertTrue(c.getItems().isEmpty());
    }

    @Test void removeItemUndoRestoresPosition() {
        MenuCategory c = addCategory("A");
        MenuItem first = revision.addItem(c, bruschetta, BigDecimal.ONE, Money.of(1), null);
        MenuItem second = revision.addItem(c, wine, BigDecimal.ONE, Money.of(1), null);
        editor.execute(new RemoveItemCommand(revision, c, first));
        assertEquals(List.of(second), c.getItems());
        assertEquals("Odebrat Bruschetta", editor.undo());
        assertEquals(List.of(first, second), c.getItems());
    }

    @Test void changeItemUndoRestoresOldValues() {
        MenuCategory c = addCategory("A");
        MenuItem item = revision.addItem(c, bruschetta, BigDecimal.ONE, Money.of(100), "pozn");
        editor.execute(new ChangeItemCommand(revision, item, BigDecimal.TEN, Money.of(80), null));
        assertEquals(BigDecimal.TEN, item.getQuantity());
        assertEquals(Money.of(80), item.getUnitPrice());
        assertNull(item.getNote());
        assertEquals("Změnit Bruschetta", editor.undo());
        assertEquals(BigDecimal.ONE, item.getQuantity());
        assertEquals(Money.of(100), item.getUnitPrice());
        assertEquals("pozn", item.getNote());
    }

    @Test void undoInReverseOrder() {
        MenuCategory c = addCategory("A");
        editor.execute(new AddItemCommand(revision, c, bruschetta, BigDecimal.ONE, Money.of(1)));
        editor.undo();
        editor.undo();
        assertTrue(revision.getCategories().isEmpty());
        assertEquals(4, editor.getLog().size());
    }

    @Test void failedCommandIsNotPushedNorLogged() {
        addCategory("A");
        assertThrows(ValidationException.class, () -> editor.execute(new AddCategoryCommand(revision, "a")));
        assertEquals(List.of("Přidat kategorii A"), editor.getLog());
        editor.undo();
        assertFalse(editor.canUndo());
        assertTrue(revision.getCategories().isEmpty());
    }

    @Test void commandsOnSharedRevisionFail() {
        MenuCategory c = addCategory("A");
        revision.addItem(c, bruschetta, BigDecimal.ONE, Money.of(1), null);
        revision.markShared();
        int logSize = editor.getLog().size();
        assertThrows(ValidationException.class, () -> editor.execute(new AddCategoryCommand(revision, "B")));
        assertThrows(ValidationException.class, () -> editor.execute(new RemoveCategoryCommand(revision, c)));
        assertEquals(logSize, editor.getLog().size());
    }
}
