package cz.vsb.catering.business.e2e;

import static org.junit.jupiter.api.Assertions.*;

import cz.vsb.catering.business.domain.Comment;
import cz.vsb.catering.business.domain.Menu;
import cz.vsb.catering.business.domain.MenuCategory;
import cz.vsb.catering.business.domain.ConflictException;
import cz.vsb.catering.business.domain.MenuRevision;
import cz.vsb.catering.business.domain.Money;
import cz.vsb.catering.business.domain.Project;
import cz.vsb.catering.business.domain.RevisionState;
import cz.vsb.catering.business.domain.ShareLink;
import cz.vsb.catering.business.domain.ValidationException;
import cz.vsb.catering.business.service.LinkNotValidException;
import cz.vsb.catering.business.service.SharedMenuView;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** UC11 Sdílet nabídku se zákazníkem a přidat komentář: main flow and alternatives 5.1-5.6 (expired,
 *  replaced or unknown link) and 7.1 (invalid comment, link expired while writing). */
class Uc11ShareCommentTest extends E2eBaseTest {

    @Test
    void mainFlowSharesRevisionAndCustomerCommentsMenuAndItem() {
        Project project = newProject("E2E UC11 Hlavní průběh");
        Menu menu = savedMenu(project, "Svatební menu");
        long menuId = menu.getMenuId();

        ShareLink link = sharing.shareRevision(menus.openLatestRevision(menuId));                     // step 2
        assertEquals("SHARED", revisionRow(menuId, 1).get("STATE"));
        assertExactlyOneActiveLink(menuId, link);
        assertEquals("http://localhost:8080/menu/" + link.getToken(), sharing.shareUrl(link));         // step 3
        assertEquals(link.getToken(), sharing.findValidLink(menuId).orElseThrow().getToken());

        SharedMenuView view = sharing.openSharedMenu(link.getToken());                                 // steps 4-5
        assertEquals(SharedMenuView.Status.VALID, view.status());
        assertEquals(1, view.revision().getRevisionNo());
        assertEquals(2, view.revision().getCategories().get(0).getItems().size());
        assertEquals(40, view.guestCount());
        assertTrue(view.title().contains("E2E UC11 Hlavní průběh"));
        assertTrue(view.revision().getComments().isEmpty());

        Comment onMenu = sharing.addComment(link.getToken(), " Jana Nováková ", "Prosíme o více dezertů.", null, null);
        Map<String, Object> c = row("SELECT * FROM comments WHERE comment_id = ?", onMenu.getId());     // step 7
        assertEquals(menuId, c.get("MENU_ID"));
        assertEquals(1L, c.get("REVISION_NO"));
        assertNull(c.get("CATEGORY_ID"));
        assertNull(c.get("PRODUCT_ID"));
        assertEquals("Jana Nováková", c.get("AUTHOR_NAME"));
        assertEquals("Prosíme o více dezertů.", c.get("BODY"));
        assertNotNull(c.get("CREATED_AT"));
        assertUnresolved(c);

        MenuCategory category = view.revision().getCategories().get(0);
        long productId = category.getItems().get(0).getProduct().getId();
        Comment onItem = sharing.addComment(link.getToken(), "Jana Nováková", "Bez lepku, prosím.", category.getId(), productId);
        Map<String, Object> ci = row("SELECT * FROM comments WHERE comment_id = ?", onItem.getId());
        assertNull(ci.get("MENU_ID"));                                                                   // ARC: item side
        assertNull(ci.get("REVISION_NO"));
        assertEquals(category.getId(), ci.get("CATEGORY_ID"));
        assertEquals(productId, ci.get("PRODUCT_ID"));
        assertUnresolved(ci);

        // step 8: both comments are visible on the revision for the customer and the worker
        assertEquals(2, sharing.openSharedMenu(link.getToken()).revision().getComments().size());
        assertEquals(2, sharing.countUnresolved(menuId, 1));
    }

    @Test
    void alt51ReplacedLinkLeadsToCurrentOffer() {
        Project project = newProject("E2E UC11 Nahrazený odkaz");
        Menu menu = savedMenu(project, "Menu");
        long menuId = menu.getMenuId();
        ShareLink first = sharing.shareRevision(menus.openLatestRevision(menuId));

        MenuRevision copy = menus.createWorkingCopy(menus.openLatestRevision(menuId));                 // UC08 2.4
        copy.addItem(copy.addCategory("Dezerty"), menus.findProducts().get(3), BigDecimal.ONE, Money.of(60), null);
        menus.saveRevision(copy, "Rev 2", worker);
        ShareLink second = sharing.shareRevision(menus.openLatestRevision(menuId));

        assertEquals("SHARED", revisionRow(menuId, 1).get("STATE"));
        assertEquals("SHARED", revisionRow(menuId, 2).get("STATE"));
        assertEquals(2, count("SELECT COUNT(*) FROM share_links WHERE menu_id = ?", menuId));
        assertEquals("N", row("SELECT active FROM share_links WHERE link_id = ?", first.getId()).get("ACTIVE"));
        assertExactlyOneActiveLink(menuId, second);
        assertEquals(2L, row("SELECT revision_no FROM share_links WHERE link_id = ?", second.getId()).get("REVISION_NO"));

        SharedMenuView old = sharing.openSharedMenu(first.getToken());                                // 5.1-5.3
        assertEquals(SharedMenuView.Status.EXPIRED, old.status());
        assertNull(old.revision());
        assertEquals(second.getToken(), old.currentToken());
        SharedMenuView current = sharing.openSharedMenu(old.currentToken());                          // 5.4-5.5
        assertEquals(SharedMenuView.Status.VALID, current.status());
        assertEquals(2, current.revision().getRevisionNo());
        assertThrows(LinkNotValidException.class,
                () -> sharing.addComment(first.getToken(), "Jana", "Starý odkaz", null, null));
        sharing.addComment(second.getToken(), "Jana", "Nová nabídka je v pořádku.", null, null);     // 5.6 -> step 6
        assertEquals(0, count("SELECT COUNT(*) FROM comments WHERE menu_id = ? AND revision_no = 1", menuId));
        assertEquals(1, count("SELECT COUNT(*) FROM comments WHERE menu_id = ? AND revision_no = 2", menuId));
    }

    @Test
    void alt51ExpiredLinkWithAndWithoutCurrentLink() {
        Project project = newProject("E2E UC11 Prošlý odkaz");
        Menu menu = savedMenu(project, "Menu");
        long menuId = menu.getMenuId();
        ShareLink link = sharing.shareRevision(menus.openLatestRevision(menuId));
        exec("UPDATE share_links SET created_at = SYSTIMESTAMP - INTERVAL '20' DAY, valid_until = SYSTIMESTAMP - INTERVAL '1' HOUR "
                + "WHERE link_id = ?", link.getId());

        SharedMenuView expired = sharing.openSharedMenu(link.getToken());
        assertEquals(SharedMenuView.Status.EXPIRED, expired.status());
        assertNull(expired.currentToken());                                                          // 5.3: contact the worker
        assertTrue(sharing.findValidLink(menuId).isEmpty());
        // link expired while the customer was writing (note after 7.3): nothing is stored
        assertThrows(LinkNotValidException.class,
                () -> sharing.addComment(link.getToken(), "Jana", "Pozdě", null, null));
        assertEquals(0, count("SELECT COUNT(*) FROM comments WHERE menu_id = ?", menuId));

        ShareLink renewed = sharing.shareRevision(menus.openLatestRevision(menuId));                  // shared again
        assertExactlyOneActiveLink(menuId, renewed);
        assertEquals(renewed.getToken(), sharing.openSharedMenu(link.getToken()).currentToken());
    }

    @Test
    void alt51UnknownLink() {
        long before = count("SELECT COUNT(*) FROM comments");
        SharedMenuView unknown = sharing.openSharedMenu("0123456789abcdef0123456789abcdef");
        assertEquals(SharedMenuView.Status.UNKNOWN, unknown.status());
        assertFalse(unknown.link().exists());
        assertThrows(LinkNotValidException.class,
                () -> sharing.addComment("0123456789abcdef0123456789abcdef", "Jana", "Text", null, null));
        assertEquals(before, count("SELECT COUNT(*) FROM comments"));
    }

    @Test
    void alt71InvalidCommentIsNotStored() {
        Project project = newProject("E2E UC11 Neplatný komentář");
        Menu menu = savedMenu(project, "Menu");
        long menuId = menu.getMenuId();
        String token = sharing.shareRevision(menus.openLatestRevision(menuId)).getToken();

        ValidationException e = assertThrows(ValidationException.class,
                () -> sharing.addComment(token, " ", "", null, null));
        assertEquals(Set.of("author", "body"), e.getFieldErrors().keySet());
        e = assertThrows(ValidationException.class,
                () -> sharing.addComment(token, "Jana", "x".repeat(Comment.MAX_BODY + 1), null, null));
        assertEquals(Set.of("body"), e.getFieldErrors().keySet());
        // item that is not on the shared revision
        long categoryId = (Long) revisionContent(menuId, 1).get(0).get("CATEGORY_ID");
        assertThrows(ValidationException.class,
                () -> sharing.addComment(token, "Jana", "Text", categoryId, -1L));
        assertEquals(0, count("""
                SELECT COUNT(*) FROM comments k LEFT JOIN menu_categories c ON c.category_id = k.category_id
                WHERE k.menu_id = ? OR c.menu_id = ?""", menuId, menuId));
        // the maximum length is accepted
        sharing.addComment(token, "Jana", "x".repeat(Comment.MAX_BODY), null, null);
        assertEquals(1, count("SELECT COUNT(*) FROM comments WHERE menu_id = ?", menuId));
    }

    @Test
    void emptyOrUnsavedRevisionCannotBeShared() {
        Project project = newProject("E2E UC11 Prázdné menu");
        Menu menu = menus.createMenu(project.getRootFolder(), "Prázdné", worker);
        MenuRevision empty = menus.openLatestRevision(menu.getMenuId());
        assertThrows(ValidationException.class, () -> sharing.shareRevision(empty));
        MenuRevision unsaved = new MenuRevision(menu.getMenuId(), 2, RevisionState.WORKING, null, null, null, false);
        assertThrows(ValidationException.class, () -> sharing.shareRevision(unsaved));
        assertEquals("WORKING", revisionRow(menu.getMenuId(), 1).get("STATE"));
        assertEquals(0, count("SELECT COUNT(*) FROM share_links WHERE menu_id = ?", menu.getMenuId()));
    }

    /** Exactly one active link of the menu, it is this one, valid 14 days from now. */
    private static void assertExactlyOneActiveLink(long menuId, ShareLink link) {
        List<Map<String, Object>> active = rows("SELECT * FROM share_links WHERE menu_id = ? AND active = 'Y'", menuId);
        assertEquals(1, active.size());
        assertEquals(link.getId(), active.get(0).get("LINK_ID"));
        assertEquals(link.getToken(), active.get(0).get("TOKEN"));
        assertEquals(32, link.getToken().length());
        assertEquals(1, count("""
                SELECT COUNT(*) FROM share_links WHERE link_id = ?
                  AND valid_until - created_at = INTERVAL '14' DAY
                  AND created_at BETWEEN SYSTIMESTAMP - INTERVAL '5' MINUTE AND SYSTIMESTAMP + INTERVAL '5' MINUTE""",
                link.getId()), "valid_until is now + 14 days");
    }

    private static void assertUnresolved(Map<String, Object> comment) {
        assertEquals("N", comment.get("RESOLVED"));
        assertNull(comment.get("RESOLUTION_NOTE"));
        assertNull(comment.get("RESOLVED_BY"));
        assertNull(comment.get("RESOLVED_AT"));
    }

    @Test
    void a07ShareOfStaleWorkingRevisionIsConflict() {
        Project project = newProject("E2E UC11 Souběh sdílení");
        Menu menu = savedMenu(project, "Menu");
        long menuId = menu.getMenuId();
        MenuRevision mine = menus.openLatestRevision(menuId);                                          // worker A
        MenuRevision theirs = menus.openLatestRevision(menuId);                                        // worker B saves first
        theirs.addItem(theirs.addCategory("Dezerty"), menus.findProducts().get(3), BigDecimal.ONE, Money.of(60), null);
        menus.saveRevision(theirs, "B", worker);
        Map<String, Object> header = revisionRow(menuId, 1);

        assertThrows(ConflictException.class, () -> sharing.shareRevision(mine));
        assertEquals(RevisionState.WORKING, mine.getState());
        assertEquals(header, revisionRow(menuId, 1));
        assertEquals(0, count("SELECT COUNT(*) FROM share_links WHERE menu_id = ?", menuId));
    }
}
