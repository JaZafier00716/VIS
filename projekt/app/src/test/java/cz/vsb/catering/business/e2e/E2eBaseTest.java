package cz.vsb.catering.business.e2e;

import static org.junit.jupiter.api.Assertions.assertEquals;

import cz.vsb.catering.business.domain.Menu;
import cz.vsb.catering.business.domain.MenuCategory;
import cz.vsb.catering.business.domain.MenuRevision;
import cz.vsb.catering.business.domain.Money;
import cz.vsb.catering.business.domain.Product;
import cz.vsb.catering.business.domain.Project;
import cz.vsb.catering.business.domain.User;
import cz.vsb.catering.business.service.AuthService;
import cz.vsb.catering.business.service.CatalogService;
import cz.vsb.catering.business.service.CustomerService;
import cz.vsb.catering.business.service.DocumentService;
import cz.vsb.catering.business.service.MenuService;
import cz.vsb.catering.business.service.ProjectService;
import cz.vsb.catering.business.service.SharingService;
import cz.vsb.catering.business.service.UserService;
import cz.vsb.catering.data.DataAccessFactory;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.io.TempDir;

/** End-to-end tests of the use cases: real service facades wired like AppContext, real Oracle mappers
 *  and a real FileDocumentStorage in a temporary folder. The post-state of every step is checked
 *  directly in the database with plain JDBC. Every test creates its own projects/users and deletes them,
 *  so the database ends exactly as the seed (checked in @AfterAll). */
abstract class E2eBaseTest {
    static final String PASSWORD = "heslo123";
    private static final List<String> COUNTED = List.of("projects", "project_nodes", "users", "user_roles", "menus",
            "menu_revisions", "menu_categories", "menu_items", "share_links", "comments",
            "customers", "products", "product_allergens", "product_price_history");

    static Properties config;
    static AuthService auth;
    static ProjectService projects;
    static DocumentService documents;
    static MenuService menus;
    static SharingService sharing;
    static UserService users;
    static CustomerService customers;
    static CatalogService catalog;
    static Path storageDir;
    static User admin;                                                 // mkral: ADMIN + MANAGER (UC16)
    static User worker;                                                // jzamostny: ADMIN + WORKER (worker actions, A-17)
    private static Map<String, Long> countsAtStart;

    final List<Long> createdProjects = new ArrayList<>();
    final List<Long> createdUsers = new ArrayList<>();

    @BeforeAll
    static void wireServices(@TempDir Path dir) throws IOException {
        config = new Properties();
        try (InputStream in = Files.newInputStream(Path.of("config/db.properties"))) {
            config.load(in);
        }
        config.setProperty("documents.dir", dir.toString());
        storageDir = dir;
        DataAccessFactory data = new DataAccessFactory(config);       // same wiring as AppContext
        Clock clock = Clock.systemDefaultZone();
        var tx = data.transactions();
        auth = new AuthService(data.userMapper(), tx);
        projects = new ProjectService(data.projectMapper(), data.customerMapper(), data.projectNodeMapper(), tx, clock);
        documents = new DocumentService(data.projectNodeMapper(), data.projectMapper(), data.documentStorage(), tx);
        menus = new MenuService(data.menuMapper(), data.projectMapper(), data.projectNodeMapper(), data.productMapper(), tx, clock);
        sharing = new SharingService(data.menuMapper(), data.projectMapper(), data.shareLinkMapper(), tx, clock, "http://localhost:8080/");
        users = new UserService(data.userMapper(), tx);
        customers = new CustomerService(data.customerMapper(), tx);
        catalog = new CatalogService(data.productMapper(), tx);
        admin = auth.login("mkral", PASSWORD);
        worker = auth.login("jzamostny", PASSWORD);
        countsAtStart = counts();
        assertSeedCounts(countsAtStart);
    }

    @AfterEach
    void deleteCreatedData() {
        // ON DELETE CASCADE removes nodes, menus, revisions, categories, items, links and comments
        createdProjects.forEach(id -> exec("DELETE FROM projects WHERE project_id = ?", id));
        createdUsers.forEach(id -> exec("DELETE FROM users WHERE user_id = ?", id));
        createdProjects.clear();
        createdUsers.clear();
    }

    @AfterAll
    static void databaseIsBackToSeed() {
        Map<String, Long> now = counts();
        assertEquals(countsAtStart, now, "row counts differ from the start of the test class");
        assertSeedCounts(now);
    }

    private static void assertSeedCounts(Map<String, Long> c) {
        assertEquals(10, c.get("projects"));
        assertEquals(77, c.get("project_nodes"));
        assertEquals(10, c.get("users"));
        assertEquals(12, c.get("menus"));
        assertEquals(20, c.get("customers"));
        assertEquals(24, c.get("products"));
    }

    private static Map<String, Long> counts() {
        Map<String, Long> result = new LinkedHashMap<>();
        for (String table : COUNTED) result.put(table, count("SELECT COUNT(*) FROM " + table));
        return result;
    }

    // ---------------------------------------------------------------- fixtures through the facades

    /** UC02 step 4 with valid data: customer 1, event in 30 days, 40 guests; deleted after the test. */
    Project newProject(String name) {
        Project project = projects.createProject(name, 1L, LocalDate.now().plusDays(30), "Ostrava", 40, worker);
        createdProjects.add(project.getId());
        return project;
    }

    /** UC02 + UC08: menu in the root folder whose revision 1 is saved with category "Předkrmy" and two items. */
    Menu savedMenu(Project project, String name) {
        Menu menu = menus.createMenu(project.getRootFolder(), name, worker);
        MenuRevision revision = menus.openLatestRevision(menu.getMenuId());
        List<Product> catalogue = menus.findProducts();
        MenuCategory category = revision.addCategory("Předkrmy");
        revision.addItem(category, catalogue.get(0), new BigDecimal("40"), Money.of("35.50"), null);
        revision.addItem(category, catalogue.get(1), new BigDecimal("2.5"), Money.of(120), "bez cibule");
        menus.saveRevision(revision, "Návrh", worker);
        return menu;
    }

    // ---------------------------------------------------------------- plain JDBC for the assertions

    static Map<String, Object> revisionRow(long menuId, int revisionNo) {
        return row("SELECT * FROM menu_revisions WHERE menu_id = ? AND revision_no = ?", menuId, revisionNo);
    }

    /** Categories and items of one revision as stored (one row per item, or per empty category). */
    static List<Map<String, Object>> revisionContent(long menuId, int revisionNo) {
        return rows("""
                SELECT c.category_id, c.name AS category, c.position, i.product_id, i.quantity, i.unit_price, i.note
                FROM menu_categories c LEFT JOIN menu_items i ON i.category_id = c.category_id
                WHERE c.menu_id = ? AND c.revision_no = ? ORDER BY c.position, i.product_id""", menuId, revisionNo);
    }

    static double num(Object value) { return ((Number) value).doubleValue(); }

    static Connection connection() throws SQLException {
        return DriverManager.getConnection(config.getProperty("db.url"), config.getProperty("db.user"),
                config.getProperty("db.password"));
    }

    /** Rows as maps keyed by upper-case column name; CHAR values are trimmed, NUMBERs become long. */
    static List<Map<String, Object>> rows(String sql, Object... params) {
        try (Connection c = connection(); PreparedStatement ps = c.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) ps.setObject(i + 1, params[i]);
            try (ResultSet rs = ps.executeQuery()) {
                ResultSetMetaData md = rs.getMetaData();
                List<Map<String, Object>> result = new ArrayList<>();
                while (rs.next()) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    for (int i = 1; i <= md.getColumnCount(); i++) {
                        Object v = rs.getObject(i);
                        if (v != null && v.getClass().getName().startsWith("oracle.sql.TIMESTAMP")) v = rs.getTimestamp(i);
                        if (v instanceof String s) v = s.trim();
                        else if (v instanceof BigDecimal b && b.scale() <= 0) v = b.longValueExact();
                        row.put(md.getColumnLabel(i).toUpperCase(), v);
                    }
                    result.add(row);
                }
                return result;
            }
        } catch (SQLException e) {
            throw new IllegalStateException(sql, e);
        }
    }

    /** Exactly one row. */
    static Map<String, Object> row(String sql, Object... params) {
        List<Map<String, Object>> r = rows(sql, params);
        assertEquals(1, r.size(), "expected exactly one row: " + sql);
        return r.get(0);
    }

    static long count(String sql, Object... params) {
        return ((Number) rows(sql, params).get(0).values().iterator().next()).longValue();
    }

    static int exec(String sql, Object... params) {
        try (Connection c = connection(); PreparedStatement ps = c.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) ps.setObject(i + 1, params[i]);
            return ps.executeUpdate();                                    // auto-commit
        } catch (SQLException e) {
            throw new IllegalStateException(sql, e);
        }
    }

    /** Number of files in the temporary document storage. */
    static long storedFiles() throws IOException {
        try (Stream<Path> s = Files.walk(storageDir)) { return s.filter(Files::isRegularFile).count(); }
    }
}
