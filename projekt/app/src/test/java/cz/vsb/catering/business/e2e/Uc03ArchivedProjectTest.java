package cz.vsb.catering.business.e2e;

import static org.junit.jupiter.api.Assertions.*;

import cz.vsb.catering.business.domain.Comment;
import cz.vsb.catering.business.domain.Document;
import cz.vsb.catering.business.domain.Folder;
import cz.vsb.catering.business.domain.Menu;
import cz.vsb.catering.business.domain.MenuRevision;
import cz.vsb.catering.business.domain.Project;
import cz.vsb.catering.business.domain.ValidationException;
import cz.vsb.catering.business.pricing.PriceStrategy;
import cz.vsb.catering.business.service.MenuExportService;
import cz.vsb.catering.business.service.SharedMenuView;
import cz.vsb.catering.data.file.PdfMenuRenderer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.api.io.TempDir;

/** UC03: an archived project is fully read-only, its content tree included. Own project with a folder,
 *  a document, a shared menu with a comment and a newer working revision is archived; then every write
 *  is refused and the database is unchanged, while reading, downloading and comparing still work. */
class Uc03ArchivedProjectTest extends E2eBaseTest {
    private static final String MESSAGE = "Projekt je archivovaný, nelze jej měnit.";
    @TempDir Path tmp;

    @Test
    void archivedProjectRefusesEveryWriteButCanBeRead() throws Exception {
        Project project = newProject("E2E UC03 Archiv");
        Folder root = project.getRootFolder();
        Folder folder = documents.createFolder(root, "Podklady");
        Document doc = documents.createDocument(folder, Files.writeString(tmp.resolve("smlouva.txt"), "Smlouva"), "smlouva.txt");
        Menu menu = savedMenu(project, "Menu");
        long menuId = menu.getMenuId();
        String token = sharing.shareRevision(menus.openLatestRevision(menuId)).getToken();
        Comment comment = sharing.addComment(token, "Jana", "Více dezertů.", null, null);
        MenuRevision copy = menus.createWorkingCopy(menus.openLatestRevision(menuId));
        menus.saveRevision(copy, "Revize 2", worker);
        projects.archiveProject(project.getId());
        Map<String, Object> before = snapshot(project.getId(), menuId);
        long filesBefore = storedFiles();

        MenuRevision working = menus.openLatestRevision(menuId);
        Path upload = Files.writeString(tmp.resolve("nabidka.txt"), "x");
        MenuExportService export = new MenuExportService(menus, documents, new PdfMenuRenderer());
        refused(() -> documents.createFolder(root, "Nová"));
        refused(() -> documents.createDocument(folder, upload, "nabidka.txt"));
        refused(() -> documents.deleteNodes(List.of(doc)));
        refused(() -> menus.createMenu(root, "Druhé menu", worker));
        refused(() -> menus.saveRevision(working, "změna", worker));
        refused(() -> export.exportPdf(menu, 1, root, PriceStrategy.WITH_VAT));
        refused(() -> sharing.shareRevision(working));
        refused(() -> sharing.addComment(token, "Jana", "Ještě jedna věc.", null, null));
        refused(() -> sharing.resolveComment(comment.getId(), "ok", worker));

        assertEquals(before, snapshot(project.getId(), menuId));
        assertEquals(filesBefore, storedFiles());

        // reading, downloading and comparing stay allowed
        assertEquals(2, projects.loadTree(project.getId()).getChildren().size());
        assertEquals(2, menus.openLatestRevision(menuId).getRevisionNo());
        assertTrue(menus.openRevision(menuId, 1).isHistorical());
        assertNotNull(menus.compareRevisions(menuId, 1, 2));
        assertEquals(1, sharing.listComments(menuId, 1).size());
        Path out = Files.createDirectory(tmp.resolve("out"));
        assertEquals(1, documents.downloadDocuments(List.of(doc), out));
        assertEquals("Smlouva", Files.readString(out.resolve("smlouva.txt")));
        SharedMenuView view = sharing.openSharedMenu(token);
        assertEquals(SharedMenuView.Status.VALID, view.status());
        assertTrue(view.readOnly());
    }

    /** UC03 reverse: archive, writes refused, reactivate, a write works again and the row is IN_PROGRESS. */
    @Test
    void reactivatedProjectAcceptsWritesAgain() {
        Project project = newProject("E2E UC03 Obnova");
        Folder root = project.getRootFolder();
        projects.archiveProject(project.getId());
        refused(() -> documents.createFolder(root, "Nová"));
        assertEquals("ARCHIVED", row("SELECT status FROM projects WHERE project_id = ?", project.getId()).get("STATUS"));

        assertEquals("Tato funkce je dostupná jen pracovníkovi.",
                assertThrows(ValidationException.class, () -> projects.reactivateProject(project.getId(), admin)).getMessage());
        projects.reactivateProject(project.getId(), worker);
        assertEquals("IN_PROGRESS", row("SELECT status FROM projects WHERE project_id = ?", project.getId()).get("STATUS"));
        assertNotNull(documents.createFolder(root, "Nová po obnově").getId());
        assertThrows(ValidationException.class, () -> projects.reactivateProject(project.getId(), worker));   // not archived any more
    }

    private static void refused(Executable write) {
        assertEquals(MESSAGE, assertThrows(ValidationException.class, write).getMessage());
    }

    /** Everything a write could change: project row, nodes, revisions with content, links, comments. */
    private static Map<String, Object> snapshot(long projectId, long menuId) {
        return Map.of(
                "project", row("SELECT name, event_date, event_place, guest_count, status FROM projects WHERE project_id = ?", projectId),
                "nodes", rows("SELECT * FROM project_nodes WHERE project_id = ? ORDER BY node_id", projectId),
                "menus", rows("SELECT * FROM menus WHERE project_id = ? ORDER BY menu_id", projectId),
                "revisions", rows("SELECT * FROM menu_revisions WHERE menu_id = ? ORDER BY revision_no", menuId),
                "content1", revisionContent(menuId, 1),
                "content2", revisionContent(menuId, 2),
                "links", rows("SELECT * FROM share_links WHERE menu_id = ? ORDER BY link_id", menuId),
                "comments", rows("SELECT * FROM comments WHERE menu_id = ? ORDER BY comment_id", menuId));
    }
}
