package cz.vsb.catering.data.oracle;

import static org.junit.jupiter.api.Assertions.*;

import cz.vsb.catering.business.domain.Comment;
import cz.vsb.catering.business.domain.Folder;
import cz.vsb.catering.business.domain.Menu;
import cz.vsb.catering.business.domain.MenuCategory;
import cz.vsb.catering.business.domain.MenuItem;
import cz.vsb.catering.business.domain.MenuRevision;
import cz.vsb.catering.business.domain.Money;
import cz.vsb.catering.business.domain.Product;
import cz.vsb.catering.business.domain.Project;
import cz.vsb.catering.business.domain.RevisionState;
import cz.vsb.catering.business.domain.ShareLink;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** Menus, revisions (Dependent Mapping of categories and items), comments (Lazy Load),
 *  share links and products, all on a throw-away project. */
class OracleMenuMapperTest {
    private static Database db;
    private static Project project;
    private static List<Product> products;
    private final OracleMenuMapper menus = new OracleMenuMapper();
    private final OracleShareLinkMapper links = new OracleShareLinkMapper();

    @BeforeAll
    static void setUp() {
        db = TestDatabase.init();
        project = db.inTransaction(() -> TestDatabase.createProject("Test mapperu menu"));
        products = db.inTransaction(new OracleProductMapper()::findAll);
    }

    @AfterAll
    static void tearDown() { TestDatabase.deleteProject(project.getId()); }

    private static LocalDateTime now() { return LocalDateTime.now().truncatedTo(ChronoUnit.MICROS); }

    /** MENU node + menus row + first revision (empty, WORKING), like MenuService.createMenu. */
    private Menu createMenu(String name) {
        return db.inTransaction(() -> {
            OracleProjectNodeMapper nodes = new OracleProjectNodeMapper();
            Folder root = nodes.loadTree(project.getId());
            Menu menu = new Menu(name);
            root.add(menu);
            nodes.insert(menu);
            menus.insertMenu(menu, project.getId());
            MenuRevision first = MenuRevision.first(menu.getMenuId());
            first.recordSave(null, 1, now());
            menus.insertRevision(first);
            return menu;
        });
    }

    /** Revision 1 filled with two categories and saved (overwrite of the stored WORKING revision). */
    private MenuRevision fillFirstRevision(long menuId) {
        MenuRevision rev = db.inTransaction(() -> menus.findRevision(menuId, 1).orElseThrow());
        MenuCategory starters = rev.addCategory("Předkrmy");
        rev.addItem(starters, products.get(0), new BigDecimal("40"), Money.of("55.50"), "bez cibule");
        rev.addItem(starters, products.get(1), new BigDecimal("2.5"), Money.of("100"), null);
        MenuCategory mains = rev.addCategory("Hlavní chod");
        rev.addItem(mains, products.get(2), BigDecimal.ONE, Money.of("250"), null);
        rev.recordSave("první verze", 1, now());
        db.inTransaction(() -> menus.overwriteRevision(rev));
        return rev;
    }

    @Test
    void productsHaveAllergens() {
        assertEquals(24, products.size());
        assertTrue(products.stream().anyMatch(p -> !p.getAllergens().isEmpty()));
        Product p = products.get(0);
        assertNotNull(p.getId());
        assertNotNull(p.getCurrentPrice());
        assertTrue(List.of(0, 12, 21).contains(p.getVatRate().intValue()));
    }

    @Test
    void revisionRoundTripOverwriteAndNewRevision() {
        Menu menu = createMenu("Menu revize");
        long menuId = menu.getMenuId();
        MenuRevision saved = fillFirstRevision(menuId);
        assertNotNull(saved.getCategories().get(0).getId());

        MenuRevision loaded = db.inTransaction(() -> menus.findRevision(menuId, 1).orElseThrow());
        assertEquals(RevisionState.WORKING, loaded.getState());
        assertEquals("první verze", loaded.getNote());
        assertEquals(1L, loaded.getCreatedBy());
        assertEquals(saved.getCreatedAt(), loaded.getCreatedAt());
        assertTrue(loaded.isStored());
        assertEquals(List.of("Předkrmy", "Hlavní chod"), loaded.getCategories().stream().map(MenuCategory::getName).toList());
        MenuCategory starters = loaded.getCategories().get(0);
        assertEquals(saved.getCategories().get(0).getId(), starters.getId());
        assertEquals(2, starters.getItems().size());
        MenuItem item = starters.findItem(products.get(0).getId()).orElseThrow();
        assertEquals(0, new BigDecimal("40").compareTo(item.getQuantity()));
        assertEquals(Money.of("55.50"), item.getUnitPrice());
        assertEquals("bez cibule", item.getNote());
        assertEquals(products.get(0).getName(), item.getProduct().getName());
        assertEquals(products.get(0).getAllergens(), item.getProduct().getAllergens());
        assertEquals(Money.of("2720.00"), loaded.totalPrice(new cz.vsb.catering.business.pricing.PriceWithoutVat()));

        // overwrite: remove a category, add an empty one
        loaded.removeCategory(loaded.getCategories().get(1));
        loaded.addCategory("Dezerty");
        loaded.recordSave("druhá", 1, now());
        db.inTransaction(() -> menus.overwriteRevision(loaded));
        MenuRevision over = db.inTransaction(() -> menus.findRevision(menuId, 1).orElseThrow());
        assertEquals("druhá", over.getNote());
        assertEquals(List.of("Předkrmy", "Dezerty"), over.getCategories().stream().map(MenuCategory::getName).toList());
        assertTrue(over.getCategories().get(1).getItems().isEmpty());

        // share, then working copy = revision 2
        over.markShared();
        db.inTransaction(() -> menus.markShared(over.getMenuId(), over.getRevisionNo(), over.getLoadedSavedAt()));
        assertFalse(db.inTransaction(() -> menus.markShared(over.getMenuId(), over.getRevisionNo(), over.getLoadedSavedAt())));
        assertFalse(db.inTransaction(() -> menus.overwriteRevision(over)));    // a SHARED revision is never overwritten
        MenuRevision copy = over.createWorkingCopy();
        copy.recordSave(null, 1, now());
        db.inTransaction(() -> menus.insertRevision(copy));

        List<MenuRevision> headers = db.inTransaction(() -> menus.findRevisionHeaders(menuId));
        assertEquals(List.of(2, 1), headers.stream().map(MenuRevision::getRevisionNo).toList());
        assertEquals(RevisionState.SHARED, headers.get(1).getState());
        assertTrue(headers.get(0).getCategories().isEmpty());              // headers only
        MenuRevision latest = db.inTransaction(() -> menus.findLatestHeader(menuId).orElseThrow());
        assertEquals(2, latest.getRevisionNo());
        assertEquals(RevisionState.WORKING, latest.getState());
        assertEquals(2, db.inTransaction(() -> menus.findRevision(menuId, 2).orElseThrow()).getCategories().size());
        assertTrue(db.inTransaction(() -> menus.findRevision(menuId, 3)).isEmpty());
    }

    @Test
    void guestCountAndTitle() {
        Menu menu = createMenu("Menu titulek");
        assertEquals(40, db.inTransaction(() -> menus.findGuestCount(menu.getMenuId())));
        assertEquals("Test mapperu menu – Menu titulek", db.inTransaction(() -> menus.findTitle(menu.getMenuId())));
        assertEquals("Svatba Novákovi – Svatební menu", db.inTransaction(() -> menus.findTitle(1)));
    }

    @Test
    void commentsAreLazyAndResolvedOnce() {
        Menu menu = createMenu("Menu komentáře");
        long menuId = menu.getMenuId();
        MenuRevision rev = fillFirstRevision(menuId);
        MenuCategory starters = rev.getCategories().get(0);
        Product product = products.get(0);
        LocalDateTime created = now();
        Comment onMenu = Comment.create(menuId, 1, null, null, "Jana", "Celkově pěkné.", created);
        Comment onItem = Comment.create(menuId, 1, starters.getId(), product.getId(), "Petr", "Méně soli.", created.plusSeconds(1));
        db.inTransaction(() -> { menus.insertComment(onMenu); menus.insertComment(onItem); });
        assertNotNull(onMenu.getId());

        MenuRevision loaded = db.inTransaction(() -> menus.findRevision(menuId, 1).orElseThrow());
        assertFalse(loaded.areCommentsLoaded());
        List<Comment> comments = loaded.getComments();                    // outside the transaction: own one
        assertTrue(loaded.areCommentsLoaded());
        assertEquals(2, comments.size());
        Comment first = comments.get(0);
        assertEquals("Celkově pěkné.", first.getBody());
        assertEquals(created, first.getCreatedAt());
        assertFalse(first.isOnItem());
        Comment second = comments.get(1);
        assertTrue(second.isOnItem());
        assertEquals(menuId, second.getMenuId());                         // taken from the category
        assertEquals(1, second.getRevisionNo());
        assertEquals(product.getName(), second.getProductName());

        Comment toResolve = db.inTransaction(() -> menus.findComment(onItem.getId()).orElseThrow());
        LocalDateTime resolvedAt = now();
        toResolve.resolve(1, "Upraveno", resolvedAt);
        assertTrue(db.inTransaction(() -> menus.updateResolution(toResolve)));
        assertFalse(db.inTransaction(() -> menus.updateResolution(toResolve)));
        Comment resolved = db.inTransaction(() -> menus.findComment(onItem.getId()).orElseThrow());
        assertTrue(resolved.isResolved());
        assertEquals("Upraveno", resolved.getResolutionNote());
        assertEquals(1L, resolved.getResolvedBy());
        assertNotNull(resolved.getResolvedByName());
        assertEquals(resolvedAt, resolved.getResolvedAt());
        assertTrue(db.inTransaction(() -> menus.findComment(-1)).isEmpty());
    }

    @Test
    void shareLinkRoundTripAndTreeFlag() {
        Menu menu = createMenu("Menu odkaz");
        long menuId = menu.getMenuId();
        ShareLink link = ShareLink.create(menuId, 1, now());
        db.inTransaction(() -> links.insert(link));
        assertNotNull(link.getId());

        ShareLink found = db.inTransaction(() -> links.findByToken(link.getToken()).orElseThrow());
        assertEquals(link.getId(), found.getId());
        assertEquals(link.getCreatedAt(), found.getCreatedAt());
        assertEquals(link.getValidUntil(), found.getValidUntil());
        assertTrue(found.isActive());
        assertEquals(1, db.inTransaction(() -> links.findByMenu(menuId)).size());
        Menu inTree = (Menu) db.inTransaction(() -> new OracleProjectNodeMapper().loadTree(project.getId()))
                .find(menu.getId()).orElseThrow();
        assertTrue(inTree.hasActiveShareLink());
        assertFalse(inTree.isDeletable());

        found.deactivate();
        db.inTransaction(() -> links.update(found));
        assertFalse(db.inTransaction(() -> links.findByToken(link.getToken()).orElseThrow()).isActive());
        assertTrue(db.inTransaction(() -> links.findByToken("x".repeat(32))).isEmpty());
        assertFalse(db.inTransaction(() -> links.findByMenu(1)).isEmpty());   // seed links
    }
}
