package cz.vsb.catering.business.service;

import static cz.vsb.catering.business.fake.Fixtures.NOW;
import static cz.vsb.catering.business.fake.Fixtures.validRevision;
import static org.junit.jupiter.api.Assertions.*;

import cz.vsb.catering.business.domain.Comment;
import cz.vsb.catering.business.domain.Folder;
import cz.vsb.catering.business.domain.Menu;
import cz.vsb.catering.business.domain.MenuRevision;
import cz.vsb.catering.business.domain.Project;
import cz.vsb.catering.business.domain.ProjectStatus;
import cz.vsb.catering.business.domain.RevisionState;
import cz.vsb.catering.business.domain.Role;
import cz.vsb.catering.business.domain.ShareLink;
import cz.vsb.catering.business.domain.User;
import cz.vsb.catering.business.domain.ValidationException;
import cz.vsb.catering.business.fake.DirectTransactionRunner;
import cz.vsb.catering.business.fake.FakeMenuMapper;
import cz.vsb.catering.business.fake.FakeProductMapper;
import cz.vsb.catering.business.fake.FakeProjectMapper;
import cz.vsb.catering.business.fake.FakeProjectNodeMapper;
import cz.vsb.catering.business.fake.FakeShareLinkMapper;
import cz.vsb.catering.business.fake.Fixtures;
import cz.vsb.catering.business.pricing.PriceStrategy;
import cz.vsb.catering.data.file.InMemoryDocumentStorage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.api.io.TempDir;

/** UC03: an archived project is fully read-only, its content tree included. Every write of the
 *  services is refused with the domain rule's message and nothing reaches the mappers; reads still work. */
class ArchivedProjectServiceTest {
    private static final long MENU = 5;
    private static final String MESSAGE = "Projekt je archivovaný, nelze jej měnit.";
    @TempDir Path tmp;
    private final FakeProjectMapper projects = new FakeProjectMapper();
    private final FakeProjectNodeMapper nodes = new FakeProjectNodeMapper();
    private final FakeMenuMapper menus = new FakeMenuMapper();
    private final FakeShareLinkMapper links = new FakeShareLinkMapper();
    private final InMemoryDocumentStorage storage = new InMemoryDocumentStorage();
    private final DirectTransactionRunner tx = new DirectTransactionRunner();
    private final DocumentService documents = new DocumentService(nodes, projects, storage, tx);
    private final MenuService menuService = new MenuService(menus, projects, nodes, new FakeProductMapper(), tx, Fixtures.CLOCK);
    private final MenuExportService export = new MenuExportService(menuService, documents,
            (revision, title, guests, strategy, target) -> Files.writeString(target, "%PDF-fake"));
    private final SharingService sharing = new SharingService(menus, projects, links, tx, Fixtures.CLOCK, "http://x");
    private final User worker = Fixtures.user("ehorakova", Role.WORKER);
    private Project project;
    private Folder root;
    private Menu menu;
    private MenuRevision working;

    { worker.setId(3L); }

    @BeforeEach void setUp() {
        // menu and revision are created while the project is still open, then it is archived
        project = new Project(null, 3L, "Setkání", LocalDate.of(2026, 10, 2), "Ostrava", 90, ProjectStatus.DONE, null);
        projects.insert(project);
        menus.projectId = project.getId();
        root = new Folder("Setkání");
        root.setProjectId(project.getId());
        nodes.insert(root);
        menu = menuService.createMenu(root, "Menu", worker);
        menu.setMenuId(MENU);
        working = validRevision(MENU, 1, RevisionState.WORKING, true);
        menus.put(working);
        menus.put(validRevision(MENU, 2, RevisionState.SHARED, true));
        project.archive();
        nodes.inserted.clear();
        menus.inserted.clear();
    }

    private static void refused(Executable write) {
        assertEquals(MESSAGE, assertThrows(ValidationException.class, write).getMessage());
    }

    @Test void domainRule() {
        refused(project::requireNotArchived);
        Project open = new Project(null, 3L, "X", LocalDate.of(2026, 12, 1), "Ostrava", 9, ProjectStatus.IN_PROGRESS, null);
        assertDoesNotThrow(open::requireNotArchived);
    }

    @Test void documentWritesAreRefused() throws Exception {
        Path file = Files.writeString(tmp.resolve("a.txt"), "x");
        refused(() -> documents.createFolder(root, "Podklady"));
        refused(() -> documents.createDocument(root, file, "a.txt"));
        refused(() -> documents.deleteNodes(List.of(menu)));
        assertTrue(nodes.inserted.isEmpty());
        assertTrue(nodes.deleted.isEmpty());
        assertEquals(List.of(menu), root.getChildren());
        assertEquals(0, storage.size());
    }

    @Test void archivedCheckRunsInsideTheInsertTransaction() throws Exception {
        int[] depth = {0}, outer = {0};
        DirectTransactionRunner counting = new DirectTransactionRunner() {
            @Override public <T> T inTransaction(java.util.function.Supplier<T> work) {
                if (depth[0] == 0) outer[0]++;
                depth[0]++;
                try { return super.inTransaction(work); } finally { depth[0]--; }
            }
        };
        DocumentService counted = new DocumentService(nodes, projects, storage, counting);
        refused(() -> counted.createFolder(root, "Podklady"));
        assertEquals(1, outer[0]);                                  // check and insert share one transaction
        Path file = Files.writeString(tmp.resolve("b.txt"), "x");
        refused(() -> counted.createDocument(root, file, "b.txt"));
        assertEquals(2, outer[0]);
        assertEquals(0, storage.size());
        assertEquals(List.of(menu), root.getChildren());
    }

    @Test void menuWritesAreRefused() {
        refused(() -> menuService.createMenu(root, "Druhé menu", worker));
        refused(() -> menuService.saveRevision(working, null, worker));
        refused(() -> export.exportPdf(menu, 2, root, PriceStrategy.WITH_VAT));
        assertTrue(nodes.inserted.isEmpty());
        assertTrue(menus.inserted.isEmpty());
        assertTrue(menus.overwritten.isEmpty());
        assertEquals(List.of(menu), root.getChildren());
    }

    @Test void sharingAndCommentsAreRefused() {
        ShareLink link = links.add(new ShareLink(MENU, 2, "a".repeat(32), NOW.minusDays(1),
                NOW.minusDays(1).plus(ShareLink.VALIDITY), true));
        menus.putComment(7, Comment.create(MENU, 2, null, null, "Jana", "Super", NOW.minusDays(1)));
        refused(() -> sharing.shareRevision(working));
        refused(() -> sharing.addComment(link.getToken(), "Jana", "Ještě jedno", null, null));
        refused(() -> sharing.resolveComment(7, "ok", worker));
        assertTrue(menus.stateUpdated.isEmpty());
        assertTrue(links.inserted.isEmpty());
        assertTrue(menus.insertedComments.isEmpty());
        assertTrue(menus.resolutions.isEmpty());
        assertTrue(link.isActive());
    }

    @Test void readingStillWorks() {
        links.add(new ShareLink(MENU, 2, "a".repeat(32), NOW.minusDays(1), NOW.minusDays(1).plus(ShareLink.VALIDITY), true));
        assertEquals(2, menuService.openLatestRevision(MENU).getRevisionNo());
        assertTrue(menuService.openRevision(MENU, 1).isHistorical());
        assertNotNull(menuService.compareRevisions(MENU, 1, 2));
        assertEquals(2, menuService.listRevisions(MENU).size());
        SharedMenuView view = sharing.openSharedMenu("a".repeat(32));
        assertEquals(SharedMenuView.Status.VALID, view.status());
        assertTrue(view.readOnly());
        assertEquals(List.of(menu), documents.listContent(root));
    }
}
