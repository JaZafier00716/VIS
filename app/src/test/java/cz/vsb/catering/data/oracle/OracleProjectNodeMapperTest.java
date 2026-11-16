package cz.vsb.catering.data.oracle;

import static org.junit.jupiter.api.Assertions.*;

import cz.vsb.catering.business.domain.Customer;
import cz.vsb.catering.business.domain.Document;
import cz.vsb.catering.business.domain.Folder;
import cz.vsb.catering.business.domain.Menu;
import cz.vsb.catering.business.domain.ProjectNode;
import cz.vsb.catering.business.domain.Project;
import cz.vsb.catering.business.domain.ProjectStatus;
import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** Projects, customers and the project tree (Single Table Inheritance + Composite). */
class OracleProjectNodeMapperTest {
    private static Database db;
    private static Project project;
    private final OracleProjectNodeMapper nodes = new OracleProjectNodeMapper();
    private final OracleProjectMapper projects = new OracleProjectMapper();
    private final OracleCustomerMapper customers = new OracleCustomerMapper();

    @BeforeAll
    static void setUp() {
        db = TestDatabase.init();
        project = db.inTransaction(() -> TestDatabase.createProject("Test mapperu stromu"));
    }

    @AfterAll
    static void tearDown() { TestDatabase.deleteProject(project.getId()); }

    @Test
    void customersAreRead() {
        List<Customer> all = db.inTransaction(customers::findAll);
        assertEquals(20, all.size());
        Customer first = db.inTransaction(() -> customers.findById(1).orElseThrow());
        assertEquals(1L, first.getId());
        assertNotNull(first.getEmail());
        assertTrue(db.inTransaction(() -> customers.findById(-1)).isEmpty());
    }

    @Test
    void projectRoundTripAndOrder() {
        Project loaded = db.inTransaction(() -> projects.findById(project.getId()).orElseThrow());
        assertEquals("Test mapperu stromu", loaded.getName());
        assertEquals(project.getEventDate(), loaded.getEventDate());
        assertEquals(40, loaded.getGuestCount());
        assertEquals(ProjectStatus.IN_PROGRESS, loaded.getStatus());
        assertEquals(1L, loaded.getCustomer().getId());
        assertEquals(1L, loaded.getResponsibleUserId());

        List<Project> all = db.inTransaction(projects::findAll);
        assertTrue(all.stream().anyMatch(p -> p.getId().equals(project.getId())));
        for (int i = 1; i < all.size(); i++)
            assertFalse(all.get(i).getEventDate().isBefore(all.get(i - 1).getEventDate()));
    }

    @Test
    void insertedTreeIsLoadedBackAndNodesDeleted() {
        db.inTransaction(() -> {
            Folder root = nodes.loadTree(project.getId());
            Folder sub = new Folder("Podklady");
            root.add(sub);
            nodes.insert(sub);
            Document doc = new Document("plan.pdf", "application/pdf", 1234, "test-mapper/" + System.nanoTime() + "/plan.pdf");
            sub.add(doc);
            nodes.insert(doc);
            Menu menu = new Menu("Testovací menu");
            root.add(menu);
            nodes.insert(menu);
            new OracleMenuMapper().insertMenu(menu, project.getId());
            assertNotNull(sub.getId());
            assertNotNull(menu.getMenuId());
        });

        Folder root = db.inTransaction(() -> nodes.loadTree(project.getId()));
        assertEquals("Test mapperu stromu", root.getName());
        assertEquals(project.getId(), root.getProjectId());
        assertNotNull(root.getCreatedAt());
        assertEquals(2, root.getChildren().size());
        Folder sub = (Folder) root.getChildren().get(0);                 // folders first
        assertEquals("Podklady", sub.getName());
        Document doc = (Document) sub.getChildren().get(0);
        assertEquals("application/pdf", doc.getContentType());
        assertEquals(1234, doc.getSizeBytes());
        assertEquals(project.getId(), doc.getProjectId());
        Menu menu = (Menu) root.getChildren().get(1);
        assertNotNull(menu.getMenuId());
        assertFalse(menu.hasActiveShareLink());

        db.inTransaction(() -> { nodes.delete(doc); nodes.delete(menu); });
        Folder after = db.inTransaction(() -> nodes.loadTree(project.getId()));
        assertEquals(1, after.getChildren().size());
        assertTrue(((Folder) after.getChildren().get(0)).getChildren().isEmpty());
        assertEquals(0, db.inTransaction(() -> Jdbc.query("SELECT 1 FROM menus WHERE menu_id = ?",
                rs -> 1, menu.getMenuId())).size());
        db.inTransaction(() -> nodes.delete(after.getChildren().get(0)));
    }

    @Test
    void duplicateSiblingNameIsConflict() {
        Folder stale = db.inTransaction(() -> nodes.loadTree(project.getId()));   // another worker inserts after this load
        Folder sub = new Folder("Duplicita");
        try {
            db.inTransaction(() -> {
                Folder root = nodes.loadTree(project.getId());
                root.add(sub);
                nodes.insert(sub);
            });
            Folder twin = new Folder("Duplicita");
            stale.add(twin);                                              // the stale tree does not know the name is taken
            assertThrows(cz.vsb.catering.business.domain.ConflictException.class,
                    () -> db.inTransaction(() -> nodes.insert(twin)));
        } finally {
            db.inTransaction(() -> nodes.delete(sub));
        }
    }

    @Test
    void seedTreeOfProjectOne() {
        Folder root = db.inTransaction(() -> nodes.loadTree(1));
        assertEquals("Svatba Novákovi", root.getName());
        assertEquals(4, root.countDocuments());
        Menu wedding = (Menu) root.getChildren().stream().filter(n -> n.getName().equals("Svatební menu"))
                .findFirst().orElseThrow();
        assertEquals(1L, wedding.getMenuId());
        ProjectNode photo = root.find(7).orElseThrow();
        assertEquals("Svatba Novákovi / Podklady / Fotky / prostory-1.jpg", photo.path());
    }

    @Test
    void allSeedProjectsLoad() {
        int count = 0;
        for (long id = 1; id <= 10; id++) {
            long projectId = id;
            count += size(db.inTransaction(() -> nodes.loadTree(projectId)));
        }
        assertEquals(77, count);
    }

    private static int size(ProjectNode node) {
        return 1 + (node instanceof Folder f ? f.getChildren().stream().mapToInt(OracleProjectNodeMapperTest::size).sum() : 0);
    }
}
