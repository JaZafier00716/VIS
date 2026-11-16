package cz.vsb.catering.business.fake;

import cz.vsb.catering.business.domain.Comment;
import cz.vsb.catering.business.domain.Menu;
import cz.vsb.catering.business.domain.MenuRevision;
import cz.vsb.catering.business.port.MenuMapper;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Revisions in a list, comments in a map; records every write for assertions. */
public class FakeMenuMapper implements MenuMapper {
    public final List<MenuRevision> revisions = new ArrayList<>();
    public final Map<Long, Comment> comments = new LinkedHashMap<>();
    public final List<MenuRevision> inserted = new ArrayList<>();
    public final List<MenuRevision> overwritten = new ArrayList<>();
    public final List<MenuRevision> stateUpdated = new ArrayList<>();
    public final List<Comment> insertedComments = new ArrayList<>();
    public final List<Comment> resolutions = new ArrayList<>();
    /** Result of updateResolution; false simulates "someone else resolved it first". */
    public boolean resolutionWritten = true;
    public int guestCount = 50;
    /** Project of every menu; put a project with this id into FakeProjectMapper to test the archive rule. */
    public long projectId = 1;
    public String title = "Svatba Novákovi – Svatební menu";
    private long nextMenuId = 100;
    private long nextCommentId = 1;

    /** Puts a revision into the "database", replacing one with the same menu and number. */
    public void put(MenuRevision revision) {
        revisions.removeIf(r -> same(r, revision.getMenuId(), revision.getRevisionNo()));
        revisions.add(revision);
    }

    public void putComment(long id, Comment comment) {
        comment.setId(id);
        comments.put(id, comment);
    }

    @Override public void insertMenu(Menu menu, long projectId) { menu.setMenuId(nextMenuId++); }

    @Override public List<MenuRevision> findRevisionHeaders(long menuId) {
        return revisions.stream().filter(r -> r.getMenuId() == menuId)
                .sorted(Comparator.comparingInt(MenuRevision::getRevisionNo).reversed()).toList();
    }

    @Override public Optional<MenuRevision> findRevision(long menuId, int revisionNo) {
        Optional<MenuRevision> found = revisions.stream().filter(r -> same(r, menuId, revisionNo)).findFirst();
        found.ifPresent(r -> r.setCommentLoader(() -> findComments(menuId, revisionNo)));
        return found;
    }

    @Override public Optional<MenuRevision> findLatestHeader(long menuId) {
        return findRevisionHeaders(menuId).stream().findFirst();
    }

    @Override public void insertRevision(MenuRevision revision) {
        inserted.add(revision);
        put(revision);
    }

    /** Set to true to simulate that another worker saved the revision meanwhile. */
    public boolean overwriteConflict;

    @Override public boolean overwriteRevision(MenuRevision revision) {
        if (overwriteConflict) return false;
        overwritten.add(revision);
        put(revision);
        return true;
    }

    /** Set to false to simulate that the revision was saved or shared by someone else meanwhile. */
    public boolean markSharedResult = true;

    @Override public boolean markShared(long menuId, int revisionNo, LocalDateTime loadedSavedAt) {
        if (!markSharedResult) return false;
        revisions.stream().filter(r -> same(r, menuId, revisionNo)).forEach(stateUpdated::add);
        return true;
    }

    @Override public List<Comment> findComments(long menuId, int revisionNo) {
        return comments.values().stream()
                .filter(c -> c.getMenuId() == menuId && c.getRevisionNo() == revisionNo).toList();
    }

    @Override public Optional<Comment> findComment(long commentId) { return Optional.ofNullable(comments.get(commentId)); }

    @Override public void insertComment(Comment comment) {
        comment.setId(nextCommentId++);
        comments.put(comment.getId(), comment);
        insertedComments.add(comment);
    }

    @Override public boolean updateResolution(Comment comment) {
        if (resolutionWritten) resolutions.add(comment);
        return resolutionWritten;
    }

    @Override public int findGuestCount(long menuId) { return guestCount; }

    @Override public String findTitle(long menuId) { return title; }

    @Override public long findProjectId(long menuId) { return projectId; }

    private static boolean same(MenuRevision r, long menuId, int revisionNo) {
        return r.getMenuId() == menuId && r.getRevisionNo() == revisionNo;
    }
}
