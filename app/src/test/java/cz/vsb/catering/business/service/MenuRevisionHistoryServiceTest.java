package cz.vsb.catering.business.service;

import static cz.vsb.catering.business.fake.Fixtures.validRevision;
import static org.junit.jupiter.api.Assertions.*;

import cz.vsb.catering.business.domain.ConflictException;
import cz.vsb.catering.business.domain.MenuRevision;
import cz.vsb.catering.business.domain.RevisionComparison;
import cz.vsb.catering.business.domain.RevisionState;
import cz.vsb.catering.business.domain.Role;
import cz.vsb.catering.business.domain.User;
import cz.vsb.catering.business.domain.ValidationException;
import cz.vsb.catering.business.fake.DirectTransactionRunner;
import cz.vsb.catering.business.fake.FakeMenuMapper;
import cz.vsb.catering.business.fake.FakeProductMapper;
import cz.vsb.catering.business.fake.FakeProjectMapper;
import cz.vsb.catering.business.fake.FakeProjectNodeMapper;
import cz.vsb.catering.business.fake.Fixtures;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

/** UC09 (older revisions are read-only) and UC10 (compareRevisions) on in-memory mappers. */
class MenuRevisionHistoryServiceTest {
    private static final long MENU = 5;
    private final FakeMenuMapper menus = new FakeMenuMapper();
    private final MenuService service = new MenuService(menus, new FakeProjectMapper(), new FakeProjectNodeMapper(), new FakeProductMapper(),
            new DirectTransactionRunner(), Fixtures.CLOCK);
    private final User worker = Fixtures.user("ehorakova", Role.WORKER);

    { worker.setId(3L); }

    /** Like the seed: 1 WORKING, 2 SHARED, 3 WORKING (newest). */
    private void seed() {
        menus.put(validRevision(MENU, 1, RevisionState.WORKING, true));
        menus.put(validRevision(MENU, 2, RevisionState.SHARED, true));
        MenuRevision third = validRevision(MENU, 3, RevisionState.WORKING, true);
        third.changeItem(Fixtures.firstItem(third), new BigDecimal("12"), Fixtures.firstItem(third).getUnitPrice(), null);
        menus.put(third);
    }

    @Test void olderWorkingAndSharedRevisionsAreReadOnlyTheNewestIsEditable() {
        seed();
        MenuRevision first = service.openRevision(MENU, 1);
        assertTrue(first.isHistorical());
        assertFalse(first.isEditable());
        ValidationException e = assertThrows(ValidationException.class, () -> first.addCategory("Dezerty"));
        assertTrue(e.getMessage().startsWith("Starší revizi"));
        assertFalse(service.openRevision(MENU, 2).isEditable());
        MenuRevision newest = service.openRevision(MENU, 3);
        assertFalse(newest.isHistorical());
        assertTrue(newest.isEditable());
    }

    @Test void workingCopyOfOlderRevisionIsSavedAfterTheNewest() {
        seed();
        assertThrows(ConflictException.class, () -> service.createWorkingCopy(service.openRevision(MENU, 1)));   // A-05: newest is WORKING
        menus.put(validRevision(MENU, 3, RevisionState.SHARED, true));
        MenuRevision copy = service.createWorkingCopy(service.openRevision(MENU, 1));
        assertEquals(4, copy.getRevisionNo());
        assertTrue(copy.isEditable());
        assertEquals(4, service.saveRevision(copy, "Návrat k revizi 1", worker));
        assertEquals(1, menus.inserted.size());
    }

    @Test void historicalWorkingRevisionCannotBeOverwritten() {
        seed();
        MenuRevision first = service.openRevision(MENU, 1);
        // the UI cannot edit it any more; even a direct save is refused by the concurrency check
        assertThrows(ConflictException.class, () -> service.saveRevision(first, null, worker));
        assertTrue(menus.overwritten.isEmpty());
    }

    @Test void compareRevisionsTakesTheLowerNumberAsOlder() {
        seed();
        RevisionComparison diff = service.compareRevisions(MENU, 3, 1);
        assertEquals(1, diff.getOlder().getRevisionNo());
        assertEquals(3, diff.getNewer().getRevisionNo());
        assertEquals(1, diff.changes().size());
        assertEquals(RevisionComparison.Status.CHANGED, diff.changes().get(0).status());
        assertThrows(ValidationException.class, () -> service.compareRevisions(MENU, 1, 9));
    }
}
