package cz.vsb.catering.business.e2e;

import static org.junit.jupiter.api.Assertions.*;

import cz.vsb.catering.business.domain.ConflictException;
import cz.vsb.catering.business.domain.Menu;
import cz.vsb.catering.business.domain.MenuCategory;
import cz.vsb.catering.business.domain.MenuItem;
import cz.vsb.catering.business.domain.MenuRevision;
import cz.vsb.catering.business.domain.Money;
import cz.vsb.catering.business.domain.Product;
import cz.vsb.catering.business.domain.Project;
import cz.vsb.catering.business.domain.RevisionState;
import cz.vsb.catering.business.domain.ShareLink;
import cz.vsb.catering.business.domain.ValidationException;
import cz.vsb.catering.business.pricing.PriceWithoutVat;
import cz.vsb.catering.business.service.MenuService;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** UC08 Upravit menu a uložit revizi: main flow (overwrite of the WORKING revision) and alternatives
 *  2.1-2.5 (working copy of a SHARED revision), 6.1 (invalid menu) and 7.1 (changed by another worker). */
class Uc08MenuRevisionTest extends E2eBaseTest {

    @Test
    void mainFlowSavesAndOverwritesWorkingRevisionKeepingItsNumber() {
        Project project = newProject("E2E UC08 Hlavní průběh");
        Menu menu = menus.createMenu(project.getRootFolder(), "Svatební menu", worker);
        long menuId = menu.getMenuId();
        List<Product> catalogue = menus.findProducts();                                               // step 3

        MenuRevision revision = menus.openLatestRevision(menuId);                                     // step 2
        assertEquals(1, revision.getRevisionNo());
        assertTrue(revision.isEditable());
        assertTrue(revision.getCategories().isEmpty());

        MenuCategory starters = revision.addCategory("Předkrmy");
        MenuItem first = revision.addItem(starters, catalogue.get(0), new BigDecimal("10"), Money.of(100), null);
        revision.addItem(starters, catalogue.get(1), new BigDecimal("2"), Money.of("50.25"), "teplé");
        revision.changeItem(first, new BigDecimal("12"), Money.of(100), "studené");               // step 4
        assertEquals(0, Money.of("1300.50").amount().compareTo(revision.totalPrice(new PriceWithoutVat()).amount()));
        assertEquals(0, new BigDecimal("32.51").compareTo(
                revision.pricePerGuest(new PriceWithoutVat(), menus.guestCount(menuId)).amount()));

        assertEquals(1, menus.saveRevision(revision, "  První návrh  ", worker));                    // steps 5-8
        Map<String, Object> rev = revisionRow(menuId, 1);
        assertEquals("WORKING", rev.get("STATE"));
        assertEquals("První návrh", rev.get("NOTE"));
        assertEquals(worker.getId(), rev.get("CREATED_BY"));
        List<Map<String, Object>> content = revisionContent(menuId, 1);
        assertEquals(2, content.size());
        Map<String, Object> firstRow = content.stream()
                .filter(r -> catalogue.get(0).getId().equals(r.get("PRODUCT_ID"))).findFirst().orElseThrow();
        assertEquals("Předkrmy", firstRow.get("CATEGORY"));
        assertEquals(1L, firstRow.get("POSITION"));
        assertEquals(12.0, num(firstRow.get("QUANTITY")));
        assertEquals(100.0, num(firstRow.get("UNIT_PRICE")));
        assertEquals("studené", firstRow.get("NOTE"));
        Object oldCategoryId = firstRow.get("CATEGORY_ID");

        // second save of the same WORKING revision overwrites it: number stays, categories are replaced
        MenuRevision again = menus.openLatestRevision(menuId);
        again.removeCategory(again.getCategories().get(0));
        MenuCategory mains = again.addCategory("Hlavní chod");
        again.addItem(mains, catalogue.get(2), new BigDecimal("40"), Money.of(250), null);
        assertEquals(1, menus.saveRevision(again, "Druhý návrh", worker));
        assertEquals(1, count("SELECT COUNT(*) FROM menu_revisions WHERE menu_id = ?", menuId));
        assertEquals("Druhý návrh", revisionRow(menuId, 1).get("NOTE"));
        List<Map<String, Object>> replaced = revisionContent(menuId, 1);
        assertEquals(1, replaced.size());
        assertEquals("Hlavní chod", replaced.get(0).get("CATEGORY"));
        assertEquals(catalogue.get(2).getId(), replaced.get(0).get("PRODUCT_ID"));
        assertEquals(0, count("SELECT COUNT(*) FROM menu_categories WHERE category_id = ?", oldCategoryId));
        assertEquals(0, count("SELECT COUNT(*) FROM menu_items WHERE category_id = ?", oldCategoryId));
        assertEquals(List.of(1), menus.listRevisions(menuId).stream().map(MenuRevision::getRevisionNo).toList());
    }

    @Test
    void alt21WorkingCopyOfSharedRevisionIsSavedAsNewRevisionAndSharedStaysUntouched() {
        Project project = newProject("E2E UC08 Pracovní kopie");
        Menu menu = savedMenu(project, "Firemní menu");
        long menuId = menu.getMenuId();
        ShareLink link = sharing.shareRevision(menus.openLatestRevision(menuId));
        Map<String, Object> sharedHeader = revisionRow(menuId, 1);
        List<Map<String, Object>> sharedContent = revisionContent(menuId, 1);
        Map<String, Object> linkRow = row("SELECT * FROM share_links WHERE link_id = ?", link.getId());

        MenuRevision shared = menus.openLatestRevision(menuId);                                       // 2.1-2.2
        assertEquals(RevisionState.SHARED, shared.getState());
        assertFalse(shared.isEditable());
        assertThrows(ValidationException.class, () -> shared.addCategory("Dezerty"));

        MenuRevision copy = menus.createWorkingCopy(shared);                                          // 2.3-2.4
        assertEquals(2, copy.getRevisionNo());
        assertTrue(copy.isEditable());
        assertFalse(copy.isStored());
        assertEquals(1, count("SELECT COUNT(*) FROM menu_revisions WHERE menu_id = ?", menuId));   // not stored yet

        MenuCategory desserts = copy.addCategory("Dezerty");
        copy.addItem(desserts, menus.findProducts().get(3), BigDecimal.ONE, Money.of(80), null);
        copy.changeItem(copy.getCategories().get(0).getItems().get(0), new BigDecimal("45"), Money.of("35.50"), null);
        assertEquals(2, menus.saveRevision(copy, "Podle přání zákazníka", worker));                  // 2.5 + step 7

        Map<String, Object> rev2 = revisionRow(menuId, 2);
        assertEquals("WORKING", rev2.get("STATE"));
        assertEquals("Podle přání zákazníka", rev2.get("NOTE"));
        List<Map<String, Object>> content2 = revisionContent(menuId, 2);
        assertEquals(3, content2.size());
        assertEquals(List.of("Předkrmy", "Předkrmy", "Dezerty"), content2.stream().map(r -> r.get("CATEGORY")).toList());
        List<Object> sharedCategoryIds = sharedContent.stream().map(r -> r.get("CATEGORY_ID")).toList();
        assertTrue(content2.stream().noneMatch(r -> sharedCategoryIds.contains(r.get("CATEGORY_ID"))), "own categories");
        // the shared revision and its link did not change
        assertEquals(sharedHeader, revisionRow(menuId, 1));
        assertEquals(sharedContent, revisionContent(menuId, 1));
        assertEquals(linkRow, row("SELECT * FROM share_links WHERE link_id = ?", link.getId()));
    }

    @Test
    void alt61InvalidMenuIsNotSaved() {
        Project project = newProject("E2E UC08 Chyby");
        Menu menu = menus.createMenu(project.getRootFolder(), "Menu s chybami", worker);
        long menuId = menu.getMenuId();
        Map<String, Object> header = revisionRow(menuId, 1);
        List<Product> catalogue = menus.findProducts();

        MenuRevision empty = menus.openLatestRevision(menuId);
        assertThrows(ValidationException.class, () -> menus.saveRevision(empty, "prázdné", worker));

        MenuRevision wrong = menus.openLatestRevision(menuId);
        MenuCategory c = wrong.addCategory("Nápoje");
        wrong.addItem(c, catalogue.get(0), BigDecimal.ZERO, Money.of(10), null);                   // quantity 0
        wrong.addItem(c, catalogue.get(0), BigDecimal.ONE, Money.of(-1), null);                    // duplicate, negative price
        ValidationException e = assertThrows(ValidationException.class, () -> menus.saveRevision(wrong, "chyby", worker));
        assertEquals(3, e.getMessage().split("\n").length);

        MenuRevision longNote = menus.openLatestRevision(menuId);
        longNote.addItem(longNote.addCategory("Polévky"), catalogue.get(1), BigDecimal.ONE, Money.of(10), null);
        assertThrows(ValidationException.class, () -> menus.saveRevision(longNote, "x".repeat(MenuService.MAX_NOTE + 1), worker));

        assertEquals(header, revisionRow(menuId, 1));                                                // 6.2 nothing saved
        assertTrue(revisionContent(menuId, 1).isEmpty());
        assertEquals(1, count("SELECT COUNT(*) FROM menu_revisions WHERE menu_id = ?", menuId));
    }

    @Test
    void alt71SecondEditorOfSameWorkingCopyGetsConflictAndNothingChanges() {
        Project project = newProject("E2E UC08 Souběh kopií");
        Menu menu = savedMenu(project, "Souběžné menu");
        long menuId = menu.getMenuId();
        sharing.shareRevision(menus.openLatestRevision(menuId));

        // two workers open the same SHARED revision and both create a working copy 2
        MenuRevision copyA = menus.createWorkingCopy(menus.openLatestRevision(menuId));
        MenuRevision copyB = menus.createWorkingCopy(menus.openLatestRevision(menuId));
        copyA.addCategory("Od pracovníka A");
        copyA.addItem(copyA.getCategories().get(1), menus.findProducts().get(4), BigDecimal.ONE, Money.of(1), null);
        copyB.addCategory("Od pracovníka B");
        copyB.addItem(copyB.getCategories().get(1), menus.findProducts().get(5), BigDecimal.ONE, Money.of(2), null);

        assertEquals(2, menus.saveRevision(copyA, "A", worker));
        Map<String, Object> headerA = revisionRow(menuId, 2);
        List<Map<String, Object>> contentA = revisionContent(menuId, 2);

        assertThrows(ConflictException.class, () -> menus.saveRevision(copyB, "B", worker));           // 7.1-7.2
        assertEquals(headerA, revisionRow(menuId, 2));
        assertEquals(contentA, revisionContent(menuId, 2));
        assertEquals(2, count("SELECT COUNT(*) FROM menu_revisions WHERE menu_id = ?", menuId));
        assertEquals(0, count("SELECT COUNT(*) FROM menu_categories WHERE menu_id = ? AND name = 'Od pracovníka B'", menuId));

        MenuRevision latest = menus.openLatestRevision(menuId);                                        // 7.3
        assertEquals(2, latest.getRevisionNo());
        assertEquals("Od pracovníka A", latest.getCategories().get(1).getName());
    }

    @Test
    void alt71WorkingRevisionSharedMeanwhileCannotBeOverwritten() {
        Project project = newProject("E2E UC08 Souběh sdílení");
        Menu menu = savedMenu(project, "Sdílené mezitím");
        long menuId = menu.getMenuId();
        MenuRevision editorA = menus.openLatestRevision(menuId);
        MenuRevision editorB = menus.openLatestRevision(menuId);

        sharing.shareRevision(editorA);                                                                // A shares rev 1
        Map<String, Object> header = revisionRow(menuId, 1);
        List<Map<String, Object>> content = revisionContent(menuId, 1);

        editorB.addCategory("Pozdní změna");
        editorB.addItem(editorB.getCategories().get(1), menus.findProducts().get(6), BigDecimal.ONE, Money.of(5), null);
        assertThrows(ConflictException.class, () -> menus.saveRevision(editorB, "B", worker));
        assertEquals("SHARED", header.get("STATE"));
        assertEquals(header, revisionRow(menuId, 1));
        assertEquals(content, revisionContent(menuId, 1));
        assertEquals(1, count("SELECT COUNT(*) FROM menu_revisions WHERE menu_id = ?", menuId));
    }

    @Test
    void alt71TwoEditorsOfSameWorkingRevisionSecondSaveGetsConflict() {
        Project project = newProject("E2E UC08 Souběh uložení");
        Menu menu = savedMenu(project, "Dva editory");
        long menuId = menu.getMenuId();
        MenuRevision editorA = menus.openLatestRevision(menuId);
        MenuRevision editorB = menus.openLatestRevision(menuId);

        editorA.addCategory("Od A");
        assertEquals(1, menus.saveRevision(editorA, "A", worker));                                       // A wins
        Map<String, Object> header = revisionRow(menuId, 1);
        List<Map<String, Object>> content = revisionContent(menuId, 1);

        editorB.addCategory("Od B");
        assertThrows(ConflictException.class, () -> menus.saveRevision(editorB, "B", worker));           // optimistic check
        assertEquals(header, revisionRow(menuId, 1));
        assertEquals(content, revisionContent(menuId, 1));

        editorA.addCategory("Od A podruhé");                                                            // A's own next save still works
        assertEquals(1, menus.saveRevision(editorA, "A2", worker));
    }

    @Test
    void a05WorkingCopyOfOlderRevisionIsRefusedWhileNewestIsWorking() {
        Project project = newProject("E2E UC08 Starší kopie");
        Menu menu = savedMenu(project, "Menu se dvěma revizemi");
        long menuId = menu.getMenuId();
        sharing.shareRevision(menus.openLatestRevision(menuId));
        MenuRevision two = menus.createWorkingCopy(menus.openLatestRevision(menuId));
        two.addCategory("Dezerty");
        two.addItem(two.getCategories().get(1), menus.findProducts().get(3), BigDecimal.ONE, Money.of(80), null);
        assertEquals(2, menus.saveRevision(two, "pracovní", worker));
        Map<String, Object> header2 = revisionRow(menuId, 2);

        MenuRevision first = menus.openRevision(menuId, 1);
        assertThrows(ConflictException.class, () -> menus.createWorkingCopy(first));
        assertEquals(header2, revisionRow(menuId, 2));
        assertEquals(2, count("SELECT COUNT(*) FROM menu_revisions WHERE menu_id = ?", menuId));
    }
}
