package cz.vsb.catering.business.service;

import static cz.vsb.catering.business.fake.Fixtures.NOW;
import static cz.vsb.catering.business.fake.Fixtures.validRevision;
import static org.junit.jupiter.api.Assertions.*;

import cz.vsb.catering.business.domain.ConflictException;
import cz.vsb.catering.business.domain.DuplicateNameException;
import cz.vsb.catering.business.domain.Folder;
import cz.vsb.catering.business.domain.Menu;
import cz.vsb.catering.business.domain.MenuRevision;
import cz.vsb.catering.business.domain.Money;
import cz.vsb.catering.business.domain.Project;
import cz.vsb.catering.business.domain.ProjectStatus;
import cz.vsb.catering.business.domain.RevisionState;
import cz.vsb.catering.business.domain.Role;
import cz.vsb.catering.business.domain.User;
import cz.vsb.catering.business.domain.ValidationException;
import cz.vsb.catering.business.fake.DirectTransactionRunner;
import cz.vsb.catering.business.fake.FakeMenuMapper;
import cz.vsb.catering.business.fake.FakeProjectMapper;
import cz.vsb.catering.business.fake.FakeProjectNodeMapper;
import cz.vsb.catering.business.fake.FakeProductMapper;
import cz.vsb.catering.business.fake.Fixtures;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class MenuServiceTest {
    private static final long MENU = 5;
    private final FakeMenuMapper menus = new FakeMenuMapper();
    private final FakeProjectNodeMapper nodes = new FakeProjectNodeMapper();
    private final FakeProjectMapper projects = new FakeProjectMapper();
    private final MenuService service = new MenuService(menus, projects, nodes,
            new FakeProductMapper(Fixtures.product(1, "Bruschetta", "100", "12")), new DirectTransactionRunner(), Fixtures.CLOCK);
    private final User worker = Fixtures.user("ehorakova", Role.WORKER);

    { worker.setId(3L); }

    private Folder root() {
        Folder root = new Folder("Svatba");
        root.setProjectId(1L);
        root.setProjectId(7L);
        nodes.insert(root);
        nodes.inserted.clear();
        return root;
    }

    // ------------------------------------------------------------ UC02 steps 8-9

    @Test void createMenuInsertsNodeMenuAndFirstRevision() {
        Folder root = root();
        Menu menu = service.createMenu(root, "Svatební menu", worker);
        assertEquals(List.of(menu), nodes.inserted);
        assertNotNull(menu.getId());
        assertNotNull(menu.getMenuId());
        assertEquals(7L, menu.getProjectId());
        assertEquals(1, menus.inserted.size());
        MenuRevision first = menus.inserted.get(0);
        assertEquals(menu.getMenuId(), first.getMenuId());
        assertEquals(1, first.getRevisionNo());
        assertEquals(RevisionState.WORKING, first.getState());
        assertEquals(3L, first.getCreatedBy());
        assertEquals(NOW, first.getCreatedAt());
        assertTrue(first.getCategories().isEmpty());
    }

    @Test void duplicateMenuNameInsertsNothing() {
        Folder root = root();
        service.createMenu(root, "Svatební menu", worker);
        nodes.inserted.clear();
        menus.inserted.clear();
        DuplicateNameException e = assertThrows(DuplicateNameException.class,
                () -> service.createMenu(root, "Svatební menu", worker));
        assertEquals("Svatební menu (2)", e.getSuggestedName());
        assertTrue(nodes.inserted.isEmpty());
        assertTrue(menus.inserted.isEmpty());
    }

    @Test void failedInsertRemovesMenuFromFolder() {
        Folder root = root();
        nodes.failOnInsert = new IllegalStateException("db down");
        assertThrows(IllegalStateException.class, () -> service.createMenu(root, "M", worker));
        assertTrue(root.getChildren().isEmpty());
    }

    // ------------------------------------------------------------ open / list

    @Test void openLatestAndSpecificRevision() {
        menus.put(validRevision(MENU, 1, RevisionState.SHARED, true));
        MenuRevision two = validRevision(MENU, 2, RevisionState.WORKING, true);
        menus.put(two);
        assertSame(two, service.openLatestRevision(MENU));
        assertEquals(1, service.openRevision(MENU, 1).getRevisionNo());
        assertThrows(ValidationException.class, () -> service.openRevision(MENU, 9));
        assertThrows(ValidationException.class, () -> service.openLatestRevision(99));
        assertEquals(List.of(2, 1), service.listRevisions(MENU).stream().map(MenuRevision::getRevisionNo).toList());
        assertEquals(1, service.findProducts().size());
        assertEquals(50, service.guestCount(MENU));
        assertEquals(menus.title, service.title(MENU));
    }

    // ------------------------------------------------------------ UC08 save

    @Test void storedWorkingRevisionIsOverwrittenKeepingNumber() {
        menus.put(validRevision(MENU, 2, RevisionState.WORKING, true));
        MenuRevision open = validRevision(MENU, 2, RevisionState.WORKING, true);   // the editor's own copy
        open.addCategory("Dezerty");
        open.addItem(open.getCategories().get(1), Fixtures.product(3, "Dort", "60", "12"), BigDecimal.ONE, Money.of(60), null);

        assertEquals(2, service.saveRevision(open, "  Přidány dezerty ", worker));
        assertEquals(1, menus.overwritten.size());
        assertEquals("Přidány dezerty", menus.overwritten.get(0).getNote());
        assertTrue(menus.inserted.isEmpty());
        assertEquals("Přidány dezerty", open.getNote());
        assertEquals(3L, open.getCreatedBy());
        assertEquals(NOW, open.getCreatedAt());
        assertTrue(open.isStored());
    }

    @Test void blankNoteIsStoredAsNull() {
        menus.put(validRevision(MENU, 2, RevisionState.WORKING, true));
        MenuRevision open = validRevision(MENU, 2, RevisionState.WORKING, true);
        service.saveRevision(open, "  ", worker);
        assertNull(open.getNote());
    }

    @Test void workingCopyOfSharedIsInsertedAsNextNumber() {
        MenuRevision shared = validRevision(MENU, 2, RevisionState.SHARED, true);
        menus.put(shared);
        MenuRevision copy = service.createWorkingCopy(shared);
        assertEquals(3, copy.getRevisionNo());
        assertFalse(copy.isStored());

        assertEquals(3, service.saveRevision(copy, null, worker));
        assertEquals(1, menus.inserted.size());
        assertEquals(3, menus.inserted.get(0).getRevisionNo());
        assertTrue(menus.overwritten.isEmpty());
        assertTrue(copy.isStored());
        assertEquals(RevisionState.SHARED, shared.getState());
        assertEquals(3, menus.findLatestHeader(MENU).orElseThrow().getRevisionNo());
    }

    @Test void conflictWhenNewerRevisionExists() {
        MenuRevision open = validRevision(MENU, 2, RevisionState.WORKING, true);
        menus.put(validRevision(MENU, 2, RevisionState.SHARED, true));
        menus.put(validRevision(MENU, 3, RevisionState.WORKING, true));
        assertThrows(ConflictException.class, () -> service.saveRevision(open, null, worker));
        assertNothingSaved();
    }

    @Test void conflictWhenRevisionWasSharedMeanwhile() {
        MenuRevision open = validRevision(MENU, 2, RevisionState.WORKING, true);
        menus.put(validRevision(MENU, 2, RevisionState.SHARED, true));
        assertThrows(ConflictException.class, () -> service.saveRevision(open, null, worker));
        assertNothingSaved();
        assertNull(open.getNote());
    }

    @Test void conflictWhenAnotherWorkingCopyWasSavedFirst() {
        MenuRevision shared = validRevision(MENU, 2, RevisionState.SHARED, true);
        menus.put(shared);
        MenuRevision mine = shared.createWorkingCopy();
        MenuRevision theirs = shared.createWorkingCopy();
        service.saveRevision(theirs, null, worker);
        menus.inserted.clear();
        assertThrows(ConflictException.class, () -> service.saveRevision(mine, null, worker));
        assertNothingSaved();
        assertFalse(mine.isStored());
    }

    @Test void invalidRevisionSavesNothing() {
        MenuRevision empty = new MenuRevision(MENU, 1, RevisionState.WORKING, null, null, null, true);
        menus.put(empty);
        assertThrows(ValidationException.class, () -> service.saveRevision(empty, null, worker));
        assertNothingSaved();

        MenuRevision open = validRevision(MENU, 1, RevisionState.WORKING, true);
        open.changeItem(Fixtures.firstItem(open), BigDecimal.ZERO, Money.of(1), null);
        assertThrows(ValidationException.class, () -> service.saveRevision(open, null, worker));
        assertNothingSaved();
    }

    @Test void tooLongNoteSavesNothing() {
        menus.put(validRevision(MENU, 2, RevisionState.WORKING, true));
        MenuRevision open = validRevision(MENU, 2, RevisionState.WORKING, true);
        ValidationException e = assertThrows(ValidationException.class,
                () -> service.saveRevision(open, "x".repeat(MenuService.MAX_NOTE + 1), worker));
        assertTrue(e.getFieldErrors().containsKey("note"));
        assertNothingSaved();
    }

    private void assertNothingSaved() {
        assertTrue(menus.inserted.isEmpty());
        assertTrue(menus.overwritten.isEmpty());
    }

    @Test void overwriteRefusedWhenSavedMeanwhileByAnotherWorker() {
        menus.put(validRevision(MENU, 2, RevisionState.WORKING, true));
        MenuRevision open = validRevision(MENU, 2, RevisionState.WORKING, true);
        menus.overwriteConflict = true;                     // the row's save time no longer matches
        assertThrows(ConflictException.class, () -> service.saveRevision(open, "x", worker));
        assertTrue(menus.overwritten.isEmpty());
        assertNull(open.getNote());                         // the object is changed only after a successful write (A-08)
        assertEquals(1L, open.getCreatedBy());
        assertEquals(NOW.minusDays(1), open.getCreatedAt());
    }

    // ------------------------------------------------------------ A-05 one WORKING revision at a time

    @Test void workingCopyRefusedWhileNewestRevisionIsWorking() {
        MenuRevision first = validRevision(MENU, 1, RevisionState.SHARED, true);
        menus.put(first);
        menus.put(validRevision(MENU, 2, RevisionState.WORKING, true));
        ConflictException e = assertThrows(ConflictException.class, () -> service.createWorkingCopy(first));
        assertEquals(MenuService.WORKING_EXISTS, e.getMessage());
    }

    @Test void historicalCopyIsNotSavedWhileNewestRevisionIsWorking() {
        MenuRevision first = validRevision(MENU, 1, RevisionState.SHARED, true);
        menus.put(first);
        menus.put(validRevision(MENU, 2, RevisionState.WORKING, true));
        first.markHistorical(2);
        MenuRevision copy = first.createWorkingCopy();                  // number 3, built without the service guard
        ConflictException e = assertThrows(ConflictException.class, () -> service.saveRevision(copy, null, worker));
        assertEquals(MenuService.WORKING_EXISTS, e.getMessage());
        assertNothingSaved();
        assertFalse(copy.isStored());
    }

    @Test void workingCopyOfOlderRevisionAllowedWhenNewestIsShared() {
        MenuRevision first = validRevision(MENU, 1, RevisionState.SHARED, true);
        menus.put(first);
        menus.put(validRevision(MENU, 2, RevisionState.SHARED, true));
        first.markHistorical(2);
        assertEquals(3, service.createWorkingCopy(first).getRevisionNo());
    }

    // ------------------------------------------------------------ A-22 archived check inside the transaction

    @Test void createMenuInArchivedProjectInsertsNothing() {
        Project project = new Project(null, 3L, "Setkání", java.time.LocalDate.of(2026, 10, 2), "Ostrava", 90,
                ProjectStatus.DONE, null);
        projects.insert(project);
        project.archive();
        Folder root = new Folder("Setkání");
        root.setProjectId(project.getId());
        assertThrows(ValidationException.class, () -> service.createMenu(root, "Menu", worker));
        assertTrue(nodes.inserted.isEmpty());
        assertTrue(menus.inserted.isEmpty());
        assertTrue(root.getChildren().isEmpty());
    }
}
