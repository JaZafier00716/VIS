package cz.vsb.catering.business.e2e;

import static org.junit.jupiter.api.Assertions.*;

import cz.vsb.catering.business.domain.Comment;
import cz.vsb.catering.business.domain.ConflictException;
import cz.vsb.catering.business.domain.Menu;
import cz.vsb.catering.business.domain.MenuItem;
import cz.vsb.catering.business.domain.MenuRevision;
import cz.vsb.catering.business.domain.Project;
import cz.vsb.catering.business.domain.User;
import cz.vsb.catering.business.domain.ValidationException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** UC13 Zpracovat komentáře zákazníka: main flow and alternatives 3.1 (change of the menu),
 *  4.1 (resolved by another worker meanwhile) and 7.1 (worker stops early). */
class Uc13ResolveCommentsTest extends E2eBaseTest {
    private long menuId;
    private String token;

    /** Shared revision 1 with a comment on the menu, one on an item and one more on the menu. */
    private List<Comment> sharedMenuWithComments(String projectName) {
        Project project = newProject(projectName);
        Menu menu = savedMenu(project, "Menu");
        menuId = menu.getMenuId();
        token = sharing.shareRevision(menus.openLatestRevision(menuId)).getToken();
        var category = menus.openRevision(menuId, 1).getCategories().get(0);
        return List.of(
                sharing.addComment(token, "Jana", "Více dezertů.", null, null),
                sharing.addComment(token, "Jana", "Méně cibule.", category.getId(),
                        category.getItems().get(1).getProduct().getId()),
                sharing.addComment(token, "Petr", "Kdy bude ochutnávka?", null, null));
    }

    @Test
    void mainFlowResolvesCommentsOneByOne() {
        List<Comment> created = sharedMenuWithComments("E2E UC13 Hlavní průběh");
        List<Map<String, Object>> untouchedBefore = commentRows(created.get(1), created.get(2));

        List<Comment> listed = sharing.listComments(menuId, 1);                                        // step 2
        assertEquals(3, listed.size());
        assertTrue(listed.stream().noneMatch(Comment::isResolved));
        assertEquals(3, sharing.countUnresolved(menuId, 1));
        assertEquals("Méně cibule.", listed.stream().filter(Comment::isOnItem).findFirst().orElseThrow().getBody());
        assertNotNull(listed.stream().filter(Comment::isOnItem).findFirst().orElseThrow().getProductName());

        Comment first = created.get(0);
        sharing.resolveComment(first.getId(), "  Doplněn dezert.  ", worker);                         // steps 3-5
        Map<String, Object> r = row("SELECT * FROM comments WHERE comment_id = ?", first.getId());
        assertEquals("Y", r.get("RESOLVED"));
        assertEquals("Doplněn dezert.", r.get("RESOLUTION_NOTE"));
        assertEquals(worker.getId(), r.get("RESOLVED_BY"));
        LocalDateTime resolvedAt = ((Timestamp) r.get("RESOLVED_AT")).toLocalDateTime();     // stored in JVM local time
        assertTrue(Duration.between(resolvedAt, LocalDateTime.now()).abs().toMinutes() < 5, "resolved_at is now");
        // 7.1-7.2: the worker stops here, the other comments did not change
        assertEquals(untouchedBefore, commentRows(created.get(1), created.get(2)));

        List<Comment> after = sharing.listComments(menuId, 1);                                         // step 6
        assertEquals(List.of(false, false, true), after.stream().map(Comment::isResolved).toList());
        assertEquals(2, sharing.countUnresolved(menuId, 1));

        sharing.resolveComment(created.get(1).getId(), null, worker);                                   // step 7
        sharing.resolveComment(created.get(2).getId(), "", worker);
        assertEquals(0, sharing.countUnresolved(menuId, 1));
        assertEquals(0, count("SELECT COUNT(*) FROM comments WHERE comment_id IN (?, ?, ?) AND resolved = 'N'",
                created.get(0).getId(), created.get(1).getId(), created.get(2).getId()));
        assertNull(row("SELECT resolution_note FROM comments WHERE comment_id = ?", created.get(2).getId()).get("RESOLUTION_NOTE"));
    }

    @Test
    void alt31CommentLeadsToNewRevisionAndNoteNamesIt() {
        List<Comment> created = sharedMenuWithComments("E2E UC13 Změna menu");
        Comment onItem = created.get(1);
        Map<String, Object> sharedHeader = revisionRow(menuId, 1);
        List<Map<String, Object>> sharedContent = revisionContent(menuId, 1);

        MenuRevision latest = menus.openLatestRevision(menuId);                                        // 3.2 -> UC08 2.1-2.4
        MenuRevision copy = menus.createWorkingCopy(latest);
        MenuItem item = copy.getCategories().get(0).findItem(onItem.getProductId()).orElseThrow();
        copy.changeItem(item, item.getQuantity(), item.getUnitPrice(), "bez cibule");
        int saved = menus.saveRevision(copy, "Podle komentáře", worker);                               // 3.3
        assertEquals(2, saved);

        sharing.resolveComment(onItem.getId(), "Upraveno v revizi č. " + saved, worker);                // 3.4-3.5
        Map<String, Object> r = row("SELECT * FROM comments WHERE comment_id = ?", onItem.getId());
        assertEquals("Y", r.get("RESOLVED"));
        assertEquals("Upraveno v revizi č. 2", r.get("RESOLUTION_NOTE"));
        assertEquals("WORKING", revisionRow(menuId, 2).get("STATE"));
        assertEquals("bez cibule", revisionContent(menuId, 2).stream()
                .filter(x -> onItem.getProductId().equals(x.get("PRODUCT_ID"))).findFirst().orElseThrow().get("NOTE"));
        assertEquals(sharedHeader, revisionRow(menuId, 1));
        assertEquals(sharedContent, revisionContent(menuId, 1));
        assertEquals(2, sharing.countUnresolved(menuId, 1));
    }

    @Test
    void alt41SecondResolveIsRefusedAndNamesTheResolver() {
        List<Comment> created = sharedMenuWithComments("E2E UC13 Souběh");
        User other = auth.login("pnovak", PASSWORD);
        Comment comment = created.get(0);

        sharing.resolveComment(comment.getId(), "Vyřídil admin", worker);
        Map<String, Object> resolved = row("SELECT * FROM comments WHERE comment_id = ?", comment.getId());

        ConflictException e = assertThrows(ConflictException.class,                                      // 4.1-4.2
                () -> sharing.resolveComment(comment.getId(), "Pozdě", other));
        assertTrue(e.getMessage().contains(worker.getFullName()), e.getMessage());
        assertEquals(resolved, row("SELECT * FROM comments WHERE comment_id = ?", comment.getId()));
        assertEquals(worker.getId(), resolved.get("RESOLVED_BY"));

        // 4.3-4.4: reloaded list shows the current state, another unresolved comment can be resolved
        assertEquals(2, sharing.countUnresolved(menuId, 1));
        sharing.resolveComment(created.get(2).getId(), "Vyřídil Novák", other);
        assertEquals(other.getId(), row("SELECT resolved_by FROM comments WHERE comment_id = ?", created.get(2).getId())
                .get("RESOLVED_BY"));
    }

    @Test
    void resolutionNoteOver200CharactersIsRefused() {
        List<Comment> created = sharedMenuWithComments("E2E UC13 Dlouhá poznámka");
        Map<String, Object> before = row("SELECT * FROM comments WHERE comment_id = ?", created.get(0).getId());
        ValidationException e = assertThrows(ValidationException.class, () ->
                sharing.resolveComment(created.get(0).getId(), "x".repeat(Comment.MAX_RESOLUTION_NOTE + 1), worker));
        assertTrue(e.getFieldErrors().containsKey("note"));
        assertEquals(before, row("SELECT * FROM comments WHERE comment_id = ?", created.get(0).getId()));
    }

    private static List<Map<String, Object>> commentRows(Comment a, Comment b) {
        return rows("SELECT * FROM comments WHERE comment_id IN (?, ?) ORDER BY comment_id", a.getId(), b.getId());
    }
}
