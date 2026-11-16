package cz.vsb.catering.business.e2e;

import static org.junit.jupiter.api.Assertions.*;

import cz.vsb.catering.business.domain.Document;
import cz.vsb.catering.business.domain.DuplicateNameException;
import cz.vsb.catering.business.domain.Folder;
import cz.vsb.catering.business.domain.Menu;
import cz.vsb.catering.business.domain.Project;
import cz.vsb.catering.business.domain.ValidationException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** UC02 Vytvořit projekt a spravovat jeho dokumenty: main flow and alternatives 4.1, 6.1, 7.1-7.12, 8.1-8.5. */
class Uc02ProjectDocumentsTest extends E2eBaseTest {

    @Test
    void mainFlowCreatesProjectRootFolderSubfolderAndMenuWithEmptyWorkingRevision() {
        assertEquals(count("SELECT COUNT(*) FROM customers"), projects.findAllCustomers().size());   // step 2

        Project project = newProject("E2E UC02 Svatba");                                              // step 4
        Map<String, Object> p = row("SELECT * FROM projects WHERE project_id = ?", project.getId());
        assertEquals("IN_PROGRESS", p.get("STATUS"));
        assertEquals(1L, p.get("CUSTOMER_ID"));
        assertEquals(worker.getId(), p.get("RESPONSIBLE_USER_ID"));
        assertEquals("E2E UC02 Svatba", p.get("NAME"));
        assertEquals(40L, p.get("GUEST_COUNT"));
        Folder root = project.getRootFolder();
        Map<String, Object> r = row("SELECT * FROM project_nodes WHERE project_id = ?", project.getId());
        assertEquals(root.getId(), r.get("NODE_ID"));
        assertEquals("FOLDER", r.get("NODE_TYPE"));
        assertNull(r.get("PARENT_ID"));
        assertTrue(documents.listContent(root).isEmpty());

        Folder sub = documents.createFolder(root, "Podklady");                                       // step 6
        Map<String, Object> f = row("SELECT * FROM project_nodes WHERE node_id = ?", sub.getId());
        assertEquals("FOLDER", f.get("NODE_TYPE"));
        assertEquals(root.getId(), f.get("PARENT_ID"));
        assertEquals(project.getId(), f.get("PROJECT_ID"));
        assertTrue(documents.listContent(sub).isEmpty());                                            // step 7

        Menu menu = menus.createMenu(sub, "Svatební menu", worker);                                   // steps 8-9
        Map<String, Object> m = row("SELECT * FROM project_nodes WHERE node_id = ?", menu.getId());
        assertEquals("MENU", m.get("NODE_TYPE"));
        assertEquals(sub.getId(), m.get("PARENT_ID"));
        Map<String, Object> menuRow = row("SELECT * FROM menus WHERE menu_id = ?", menu.getMenuId());
        assertEquals(menu.getId(), menuRow.get("NODE_ID"));
        assertEquals(project.getId(), menuRow.get("PROJECT_ID"));
        assertEquals("Svatební menu", menuRow.get("NAME"));
        Map<String, Object> rev = row("SELECT * FROM menu_revisions WHERE menu_id = ?", menu.getMenuId());
        assertEquals(1L, rev.get("REVISION_NO"));
        assertEquals("WORKING", rev.get("STATE"));
        assertEquals(worker.getId(), rev.get("CREATED_BY"));
        assertEquals(0, count("SELECT COUNT(*) FROM menu_categories WHERE menu_id = ?", menu.getMenuId()));
        assertEquals(3, count("SELECT COUNT(*) FROM project_nodes WHERE project_id = ?", project.getId()));

        // the stored tree (Composite) has the same shape
        Folder tree = projects.loadTree(project.getId());
        Folder loadedSub = (Folder) tree.find(sub.getId()).orElseThrow();
        assertEquals(List.of(menu.getId()), loadedSub.getChildren().stream().map(n -> n.getId()).toList());
    }

    @Test
    void alt41InvalidProjectDataIsNotStored() {
        long before = count("SELECT COUNT(*) FROM projects");
        ValidationException e = assertThrows(ValidationException.class, () ->
                projects.createProject(" ", null, LocalDate.now().minusDays(1), "", 0, worker));
        assertEquals(java.util.Set.of("name", "customer", "eventDate", "eventPlace", "guestCount"),
                e.getFieldErrors().keySet());
        assertEquals(before, count("SELECT COUNT(*) FROM projects"));
        assertEquals(0, count("SELECT COUNT(*) FROM projects WHERE TRIM(name) IS NULL"));
    }

    @Test
    void alt61DuplicateFolderNameIsRejected() {
        Project project = newProject("E2E UC02 Duplicitní složka");
        Folder root = project.getRootFolder();
        documents.createFolder(root, "Podklady");
        DuplicateNameException e = assertThrows(DuplicateNameException.class,
                () -> documents.createFolder(root, "podklady"));                // case-insensitive
        assertEquals("podklady (2)", e.getSuggestedName());       // suggestion keeps the typed name
        assertEquals(1, count("SELECT COUNT(*) FROM project_nodes WHERE project_id = ? AND parent_id = ?",
                project.getId(), root.getId()));
        assertEquals(1, documents.listContent(root).size());
    }

    @Test
    void alt81DuplicateMenuNameOffersFreeNameAndRenameSucceeds() {
        Project project = newProject("E2E UC02 Duplicitní menu");
        Folder root = project.getRootFolder();
        menus.createMenu(root, "Svatební menu", worker);
        DuplicateNameException e = assertThrows(DuplicateNameException.class,
                () -> menus.createMenu(root, "Svatební menu", worker));               // 8.1
        assertEquals("Svatební menu (2)", e.getSuggestedName());
        assertEquals(1, count("SELECT COUNT(*) FROM menus WHERE project_id = ?", project.getId()));
        assertEquals(1, documents.listContent(root).size());

        Menu renamed = menus.createMenu(root, e.getSuggestedName(), worker);          // 8.2-8.5
        assertEquals("Svatební menu (2)", row("SELECT name FROM project_nodes WHERE node_id = ?", renamed.getId()).get("NAME"));
        assertEquals(2, count("SELECT COUNT(*) FROM menus WHERE project_id = ?", project.getId()));
        assertEquals(1, count("SELECT COUNT(*) FROM menu_revisions WHERE menu_id = ? AND revision_no = 1 AND state = 'WORKING'",
                renamed.getMenuId()));
    }

    @Test
    void alt71UploadStoresFileAndMetadataAndDownloadChangesNothing(@TempDir Path station) throws IOException {
        Project project = newProject("E2E UC02 Dokumenty");
        Folder sub = documents.createFolder(project.getRootFolder(), "Smlouvy");
        Path file = Files.writeString(station.resolve("smlouva.txt"), "Smlouva o cateringu – obsah");
        long filesBefore = storedFiles();

        Document doc = documents.createDocument(sub, file, "smlouva.txt");                         // 7.3-7.6
        Map<String, Object> d = row("SELECT * FROM project_nodes WHERE node_id = ?", doc.getId());
        assertEquals("DOCUMENT", d.get("NODE_TYPE"));
        assertEquals(sub.getId(), d.get("PARENT_ID"));
        assertEquals("smlouva.txt", d.get("NAME"));
        assertEquals(Files.size(file), d.get("SIZE_BYTES"));
        assertNotNull(d.get("CONTENT_TYPE"));
        String key = (String) d.get("STORAGE_KEY");
        assertEquals(doc.getStorageKey(), key);
        assertEquals(Files.readString(file), Files.readString(storageDir.resolve(key)));
        assertEquals(filesBefore + 1, storedFiles());
        assertEquals(List.of(doc), documents.listContent(sub));                                   // 7.7

        // 7.5 -> 8.1: same name again stores neither a row nor a file
        DuplicateNameException e = assertThrows(DuplicateNameException.class,
                () -> documents.createDocument(sub, file, "smlouva.txt"));
        assertEquals("smlouva (2).txt", e.getSuggestedName());
        assertEquals(1, count("SELECT COUNT(*) FROM project_nodes WHERE parent_id = ?", sub.getId()));
        assertEquals(filesBefore + 1, storedFiles());

        // 7.8-7.12: download copies the content, database and storage stay the same
        long nodesBefore = count("SELECT COUNT(*) FROM project_nodes WHERE project_id = ?", project.getId());
        Path target = Files.createDirectory(station.resolve("cil"));
        assertEquals(1, documents.downloadDocuments(List.of(doc), target));
        assertEquals(Files.readString(file), Files.readString(target.resolve("smlouva.txt")));
        assertEquals(nodesBefore, count("SELECT COUNT(*) FROM project_nodes WHERE project_id = ?", project.getId()));
        assertEquals(filesBefore + 1, storedFiles());
        assertEquals(key, row("SELECT storage_key FROM project_nodes WHERE node_id = ?", doc.getId()).get("STORAGE_KEY"));
    }

    @Test
    void alt74FileOver20MbIsRejected(@TempDir Path station) throws IOException {
        Project project = newProject("E2E UC02 Velký soubor");
        Path big = station.resolve("pudorys.pdf");
        try (RandomAccessFile raf = new RandomAccessFile(big.toFile(), "rw")) {
            raf.setLength(20L * 1024 * 1024 + 1);                                  // sparse, fast
        }
        long filesBefore = storedFiles();
        ValidationException e = assertThrows(ValidationException.class,
                () -> documents.createDocument(project.getRootFolder(), big, "pudorys.pdf"));
        assertTrue(e.getFieldErrors().containsKey("file"));
        assertEquals(1, count("SELECT COUNT(*) FROM project_nodes WHERE project_id = ?", project.getId()));
        assertEquals(filesBefore, storedFiles());
        assertTrue(documents.listContent(project.getRootFolder()).isEmpty());
    }
}
