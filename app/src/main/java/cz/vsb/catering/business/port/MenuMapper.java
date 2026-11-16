package cz.vsb.catering.business.port;

import cz.vsb.catering.business.domain.Comment;
import cz.vsb.catering.business.domain.Menu;
import cz.vsb.catering.business.domain.MenuRevision;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/** Data Mapper for menus, menu_revisions, menu_categories, menu_items and comments
 *  (categories and items are a Dependent Mapping of their revision). */
public interface MenuMapper {
    /** Inserts the menus row for an already stored MENU node and sets menuId. */
    void insertMenu(Menu menu, long projectId);

    /** Revision headers (number, state, note, date) without categories, newest first. */
    List<MenuRevision> findRevisionHeaders(long menuId);

    /** Whole revision with categories, items and products; comments get a lazy loader. */
    Optional<MenuRevision> findRevision(long menuId, int revisionNo);

    /** Header (number + state) of the newest revision, for the concurrency check of UC08 7.1. */
    Optional<MenuRevision> findLatestHeader(long menuId);

    /** Inserts a new revision with categories and items. */
    void insertRevision(MenuRevision revision);

    /** Overwrites a WORKING revision: header, then categories and items are replaced.
     *  Optimistic check: only if the row is still WORKING and created_at still equals revision.getLoadedSavedAt();
     *  returns false (nothing written) when another worker saved or shared it in the meantime. */
    boolean overwriteRevision(MenuRevision revision);

    /** UC11 step 2: WORKING becomes SHARED only if the row is still WORKING and saved at loadedSavedAt;
     *  false = someone saved or shared it meanwhile. */
    boolean markShared(long menuId, int revisionNo, LocalDateTime loadedSavedAt);

    /** Comments of a revision and of its items, with product name and resolver's name. */
    List<Comment> findComments(long menuId, int revisionNo);

    Optional<Comment> findComment(long commentId);

    void insertComment(Comment comment);

    /** Writes the resolution only if the row is still unresolved; false = someone was faster (UC13 4.1). */
    boolean updateResolution(Comment comment);

    /** Guest count of the project that owns the menu (price per guest). */
    int findGuestCount(long menuId);

    /** Id of the project that owns the menu (UC03 archived-project check). */
    long findProjectId(long menuId);

    /** Project name and menu name for headings, e.g. "Svatba Novákovi – Svatební menu". */
    String findTitle(long menuId);
}
