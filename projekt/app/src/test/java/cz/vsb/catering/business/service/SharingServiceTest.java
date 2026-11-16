package cz.vsb.catering.business.service;

import static cz.vsb.catering.business.fake.Fixtures.NOW;
import static cz.vsb.catering.business.fake.Fixtures.validRevision;
import static org.junit.jupiter.api.Assertions.*;

import cz.vsb.catering.business.domain.Comment;
import cz.vsb.catering.business.domain.ConflictException;
import cz.vsb.catering.business.domain.MenuRevision;
import cz.vsb.catering.business.domain.RevisionState;
import cz.vsb.catering.business.domain.Role;
import cz.vsb.catering.business.domain.ShareLink;
import cz.vsb.catering.business.domain.User;
import cz.vsb.catering.business.domain.ValidationException;
import cz.vsb.catering.business.fake.DirectTransactionRunner;
import cz.vsb.catering.business.fake.FakeMenuMapper;
import cz.vsb.catering.business.fake.FakeProjectMapper;
import cz.vsb.catering.business.fake.FakeShareLinkMapper;
import cz.vsb.catering.business.fake.Fixtures;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class SharingServiceTest {
    private static final long MENU = 5;
    private final FakeMenuMapper menus = new FakeMenuMapper();
    private final FakeShareLinkMapper links = new FakeShareLinkMapper();
    private final FakeProjectMapper projects = new FakeProjectMapper();
    private final SharingService service = new SharingService(menus, projects, links, new DirectTransactionRunner(),
            Fixtures.CLOCK, "http://localhost:8080/");
    private final User worker = Fixtures.user("ehorakova", Role.WORKER);

    { worker.setId(3L); }

    private ShareLink link(int revisionNo, String token, int createdDaysAgo, boolean active) {
        return links.add(new ShareLink(MENU, revisionNo, token, NOW.minusDays(createdDaysAgo),
                NOW.minusDays(createdDaysAgo).plus(ShareLink.VALIDITY), active));
    }

    // ------------------------------------------------------------ UC11 step 2

    @Test void shareRevisionMarksSharedAndReplacesLinks() {
        ShareLink old = link(1, "a".repeat(32), 3, true);
        ShareLink inactive = link(1, "b".repeat(32), 20, false);
        MenuRevision revision = validRevision(MENU, 2, RevisionState.WORKING, true);
        menus.put(revision);

        ShareLink created = service.shareRevision(revision);

        assertEquals(RevisionState.SHARED, revision.getState());
        assertEquals(List.of(revision), menus.stateUpdated);
        assertFalse(old.isActive());
        assertEquals(List.of(old), links.updated);
        assertFalse(inactive.isActive());
        assertEquals(List.of(created), links.inserted);
        assertTrue(created.isActive());
        assertEquals(2, created.getRevisionNo());
        assertEquals(MENU, created.getMenuId());
        assertEquals(NOW, created.getCreatedAt());
        assertEquals(NOW.plusDays(14), created.getValidUntil());
        assertEquals(32, created.getToken().length());
        assertEquals("http://localhost:8080/menu/" + created.getToken(), service.shareUrl(created));
        assertSame(created, service.findValidLink(MENU).orElseThrow());
    }

    @Test void alreadySharedRevisionGetsNewLinkWithoutStateChange() {
        MenuRevision shared = validRevision(MENU, 2, RevisionState.SHARED, true);
        menus.put(shared);
        link(2, "a".repeat(32), 3, true);
        ShareLink created = service.shareRevision(shared);
        assertTrue(menus.stateUpdated.isEmpty());
        assertEquals(List.of(created), links.inserted);
    }

    @Test void staleShareLeavesRevisionWorkingAndWritesNothing() {
        MenuRevision revision = validRevision(MENU, 2, RevisionState.WORKING, true);
        menus.put(revision);
        menus.markSharedResult = false;                                  // saved or shared by someone else meanwhile
        revision.addListener(r -> fail("listener must not be told"));
        assertThrows(ConflictException.class, () -> service.shareRevision(revision));
        assertEquals(RevisionState.WORKING, revision.getState());
        assertTrue(links.inserted.isEmpty());
    }

    @Test void unsavedRevisionIsRejected() {
        MenuRevision copy = validRevision(MENU, 2, RevisionState.SHARED, true).createWorkingCopy();
        assertThrows(ValidationException.class, () -> service.shareRevision(copy));
        assertEquals(RevisionState.WORKING, copy.getState());
        assertTrue(links.inserted.isEmpty());
    }

    @Test void invalidRevisionIsRejected() {
        MenuRevision empty = new MenuRevision(MENU, 1, RevisionState.WORKING, null, null, null, true);
        menus.put(empty);
        assertThrows(ValidationException.class, () -> service.shareRevision(empty));
        assertTrue(links.inserted.isEmpty());
        assertTrue(menus.stateUpdated.isEmpty());
    }

    @Test void olderWorkingRevisionIsConflict() {
        MenuRevision open = validRevision(MENU, 2, RevisionState.WORKING, true);
        menus.put(validRevision(MENU, 3, RevisionState.WORKING, true));
        assertThrows(ConflictException.class, () -> service.shareRevision(open));
        assertEquals(RevisionState.WORKING, open.getState());
        assertTrue(links.inserted.isEmpty());
    }

    // ------------------------------------------------------------ UC11 step 5, 5.1-5.3

    @Test void openValidLinkShowsRevisionWithComments() {
        MenuRevision shared = validRevision(MENU, 2, RevisionState.SHARED, true);
        menus.put(shared);
        menus.putComment(1, Comment.create(MENU, 2, null, null, "Jana", "Super", NOW.minusDays(1)));
        ShareLink valid = link(2, "a".repeat(32), 1, true);

        SharedMenuView view = service.openSharedMenu(valid.getToken());

        assertEquals(SharedMenuView.Status.VALID, view.status());
        assertSame(shared, view.revision());
        assertSame(valid, view.link());
        assertTrue(view.revision().areCommentsLoaded());
        assertEquals(1, view.revision().getComments().size());
        assertEquals(menus.title, view.title());
        assertEquals(50, view.guestCount());
        assertNull(view.currentToken());
    }

    @Test void expiredLinkOffersNewerValidLink() {
        ShareLink expired = link(1, "a".repeat(32), 15, true);
        ShareLink current = link(2, "b".repeat(32), 1, true);
        SharedMenuView view = service.openSharedMenu(expired.getToken());
        assertEquals(SharedMenuView.Status.EXPIRED, view.status());
        assertNull(view.revision());
        assertEquals(current.getToken(), view.currentToken());
    }

    @Test void replacedLinkWithoutValidSuccessorHasNoCurrentToken() {
        ShareLink deactivated = link(1, "a".repeat(32), 1, false);
        link(1, "c".repeat(32), 30, true);                       // active but expired
        SharedMenuView view = service.openSharedMenu(deactivated.getToken());
        assertEquals(SharedMenuView.Status.EXPIRED, view.status());
        assertNull(view.currentToken());
    }

    @Test void unknownTokenIsSpecialCase() {
        SharedMenuView view = service.openSharedMenu("neexistuje");
        assertEquals(SharedMenuView.Status.UNKNOWN, view.status());
        assertFalse(view.link().exists());
        assertNull(view.revision());
    }

    // ------------------------------------------------------------ UC11 step 7

    @Test void addCommentToWholeMenu() {
        ShareLink valid = link(2, "a".repeat(32), 1, true);
        Comment c = service.addComment(valid.getToken(), " Jana ", " Méně soli ", 10L, null);
        assertEquals(List.of(c), menus.insertedComments);
        assertNotNull(c.getId());
        assertEquals(MENU, c.getMenuId());
        assertEquals(2, c.getRevisionNo());
        assertNull(c.getCategoryId(), "category is ignored for a comment on the whole menu");
        assertNull(c.getProductId());
        assertEquals("Jana", c.getAuthorName());
        assertEquals(NOW, c.getCreatedAt());
        assertFalse(c.isResolved());
    }

    @Test void addCommentToItem() {
        menus.put(validRevision(MENU, 2, RevisionState.SHARED, true));
        ShareLink valid = link(2, "a".repeat(32), 1, true);
        Comment c = service.addComment(valid.getToken(), "Jana", "Bez cibule", 10L, 1L);
        assertEquals(10L, c.getCategoryId());
        assertEquals(1L, c.getProductId());
        assertTrue(c.isOnItem());
    }

    @Test void itemNotInRevisionIsRejected() {
        menus.put(validRevision(MENU, 2, RevisionState.SHARED, true));
        ShareLink valid = link(2, "a".repeat(32), 1, true);
        assertThrows(ValidationException.class, () -> service.addComment(valid.getToken(), "Jana", "x", 10L, 99L));
        assertThrows(ValidationException.class, () -> service.addComment(valid.getToken(), "Jana", "x", 11L, 1L));
        assertThrows(ValidationException.class, () -> service.addComment(valid.getToken(), "Jana", "x", null, 1L));
        assertTrue(menus.insertedComments.isEmpty());
    }

    @Test void invalidCommentIsNotStored() {
        ShareLink valid = link(2, "a".repeat(32), 1, true);
        ValidationException e = assertThrows(ValidationException.class,
                () -> service.addComment(valid.getToken(), "", "x".repeat(2001), null, null));
        assertEquals(Set.of("author", "body"), e.getFieldErrors().keySet());
        assertTrue(menus.insertedComments.isEmpty());
    }

    @Test void expiredOrUnknownLinkRejectsComment() {
        ShareLink expired = link(2, "a".repeat(32), 15, true);
        assertThrows(LinkNotValidException.class, () -> service.addComment(expired.getToken(), "Jana", "x", null, null));
        assertThrows(LinkNotValidException.class, () -> service.addComment("neexistuje", "Jana", "x", null, null));
        assertTrue(menus.insertedComments.isEmpty());
    }

    // ------------------------------------------------------------ UC13

    @Test void listCommentsUnresolvedFirstThenNewest() {
        Comment oldOpen = Comment.create(MENU, 2, null, null, "A", "old open", NOW.minusDays(3));
        Comment newOpen = Comment.create(MENU, 2, null, null, "B", "new open", NOW.minusDays(1));
        Comment resolvedNew = Comment.create(MENU, 2, null, null, "C", "resolved new", NOW.minusHours(1));
        resolvedNew.resolve(3L, null, NOW);
        Comment otherRevision = Comment.create(MENU, 1, null, null, "D", "rev 1", NOW);
        menus.putComment(1, resolvedNew);
        menus.putComment(2, oldOpen);
        menus.putComment(3, otherRevision);
        menus.putComment(4, newOpen);

        assertEquals(List.of(newOpen, oldOpen, resolvedNew), service.listComments(MENU, 2));
        assertEquals(2, service.countUnresolved(MENU, 2));
    }

    @Test void resolveComment() {
        menus.putComment(1, Comment.create(MENU, 2, null, null, "A", "x", NOW.minusDays(1)));
        Comment c = service.resolveComment(1, " Upraveno v revizi č. 3 ", worker);
        assertTrue(c.isResolved());
        assertEquals("Upraveno v revizi č. 3", c.getResolutionNote());
        assertEquals(3L, c.getResolvedBy());
        assertEquals(NOW, c.getResolvedAt());
        assertEquals(List.of(c), menus.resolutions);
    }

    @Test void resolveConflictWhenRowAlreadyResolved() {
        menus.putComment(1, Comment.create(MENU, 2, null, null, "A", "x", NOW.minusDays(1)));
        menus.resolutionWritten = false;
        assertThrows(ConflictException.class, () -> service.resolveComment(1, null, worker));
    }

    @Test void resolveConflictWhenLoadedCommentIsResolved() {
        menus.putComment(1, new Comment(MENU, 2, null, null, null, "A", "x", NOW, true, null, 4L, "Eva Horáková", NOW));
        ConflictException e = assertThrows(ConflictException.class, () -> service.resolveComment(1, null, worker));
        assertTrue(e.getMessage().contains("Eva Horáková"));
        assertTrue(menus.resolutions.isEmpty());
    }

    @Test void resolveUnknownComment() {
        assertThrows(ValidationException.class, () -> service.resolveComment(42, null, worker));
    }
}
