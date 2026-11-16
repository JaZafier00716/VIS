package cz.vsb.catering.business.e2e;

import static org.junit.jupiter.api.Assertions.*;

import cz.vsb.catering.business.domain.Document;
import cz.vsb.catering.business.domain.Folder;
import cz.vsb.catering.business.domain.Menu;
import cz.vsb.catering.business.domain.Project;
import cz.vsb.catering.business.domain.ProjectNode;
import cz.vsb.catering.business.domain.ShareLink;
import cz.vsb.catering.business.domain.ValidationException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** UC19 Smazat položku projektu: all selected items or none; a non-empty folder, a menu with an active
 *  share link and the root folder block the whole deletion. */
class Uc19DeleteTest extends E2eBaseTest {
    private Project project;
    private Folder root;
    private Folder contracts;          // non-empty: contains the document
    private Folder empty;
    private Document document;
    private Menu plainMenu;
    private Menu sharedMenu;
    private ShareLink link;

    @BeforeEach
    void buildTree(@TempDir Path station) throws IOException {
        project = newProject("E2E UC19 Mazání");
        root = project.getRootFolder();
        contracts = documents.createFolder(root, "Smlouvy");
        empty = documents.createFolder(root, "Prázdná");
        document = documents.createDocument(contracts, Files.writeString(station.resolve("smlouva.txt"), "obsah"), "smlouva.txt");
        plainMenu = menus.createMenu(root, "Menu bez odkazu", worker);
        sharedMenu = savedMenu(project, "Sdílené menu");
        link = sharing.shareRevision(menus.openLatestRevision(sharedMenu.getMenuId()));
        sharing.addComment(link.getToken(), "Jana", "Komentář", null, null);
    }

    @Test
    void rootFolderCannotBeDeleted() {
        List<Map<String, Object>> before = nodes();
        ValidationException e = assertThrows(ValidationException.class, () -> documents.deleteNodes(List.of(root)));
        assertTrue(e.getMessage().contains("Kořenovou složku"), e.getMessage());
        assertEquals(before, nodes());
    }

    @Test
    void menuWithActiveLinkBlocksWholeSelection() {
        List<Map<String, Object>> before = nodes();
        ValidationException e = assertThrows(ValidationException.class,
                () -> documents.deleteNodes(List.of(document, empty, plainMenu, sharedMenu)));
        assertTrue(e.getMessage().startsWith("Nic nebylo smazáno."), e.getMessage());
        assertTrue(e.getMessage().contains("Sdílené menu"), e.getMessage());
        assertEquals(before, nodes());
        assertTrue(Files.exists(storageDir.resolve(document.getStorageKey())));
        assertEquals(2, count("SELECT COUNT(*) FROM menus WHERE project_id = ?", project.getId()));
    }

    @Test
    void nonEmptyFolderBlocksWholeSelection() {
        List<Map<String, Object>> before = nodes();
        ValidationException e = assertThrows(ValidationException.class,
                () -> documents.deleteNodes(List.of(empty, contracts)));
        assertTrue(e.getMessage().contains("Smlouvy"), e.getMessage());
        assertEquals(before, nodes());
        assertTrue(Files.exists(storageDir.resolve(document.getStorageKey())));
    }

    @Test
    void documentEmptyFolderAndMenuAreDeletedTogetherWithFileAndRevisions() {
        long plainMenuId = plainMenu.getMenuId();
        Path file = storageDir.resolve(document.getStorageKey());
        assertTrue(Files.exists(file));

        documents.deleteNodes(List.of(document, empty, plainMenu));
        for (ProjectNode n : List.of(document, empty, plainMenu))
            assertEquals(0, count("SELECT COUNT(*) FROM project_nodes WHERE node_id = ?", n.getId()));
        assertEquals(0, count("SELECT COUNT(*) FROM menus WHERE menu_id = ?", plainMenuId));
        assertEquals(0, count("SELECT COUNT(*) FROM menu_revisions WHERE menu_id = ?", plainMenuId));
        assertFalse(Files.exists(file), "file removed from the document storage");
        assertTrue(contracts.getChildren().isEmpty());

        documents.deleteNodes(List.of(contracts));                    // the folder is empty now
        assertEquals(0, count("SELECT COUNT(*) FROM project_nodes WHERE node_id = ?", contracts.getId()));
        assertEquals(List.of(root.getId(), sharedMenu.getId()), nodes().stream().map(r -> r.get("NODE_ID")).toList());
    }

    @Test
    void menuWithExpiredLinkIsDeletedWithRevisionsLinksAndComments() {
        long menuId = sharedMenu.getMenuId();
        exec("UPDATE share_links SET created_at = SYSTIMESTAMP - INTERVAL '20' DAY, valid_until = SYSTIMESTAMP - INTERVAL '1' HOUR "
                + "WHERE link_id = ?", link.getId());

        documents.deleteNodes(List.of(sharedMenu));
        assertEquals(0, count("SELECT COUNT(*) FROM project_nodes WHERE node_id = ?", sharedMenu.getId()));
        assertEquals(0, count("SELECT COUNT(*) FROM menus WHERE menu_id = ?", menuId));
        assertEquals(0, count("SELECT COUNT(*) FROM menu_revisions WHERE menu_id = ?", menuId));
        assertEquals(0, count("SELECT COUNT(*) FROM share_links WHERE menu_id = ?", menuId));
        assertEquals(0, count("SELECT COUNT(*) FROM comments WHERE menu_id = ?", menuId));
    }

    private List<Map<String, Object>> nodes() {
        return rows("SELECT node_id, parent_id, node_type, name, storage_key FROM project_nodes WHERE project_id = ? ORDER BY node_id",
                project.getId());
    }
}
