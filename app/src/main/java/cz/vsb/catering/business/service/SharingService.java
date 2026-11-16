package cz.vsb.catering.business.service;

import cz.vsb.catering.business.domain.AccessPolicy;
import cz.vsb.catering.business.domain.Comment;
import cz.vsb.catering.business.domain.ConflictException;
import cz.vsb.catering.business.domain.MenuRevision;
import cz.vsb.catering.business.domain.Project;
import cz.vsb.catering.business.domain.RevisionState;
import cz.vsb.catering.business.domain.ShareLink;
import cz.vsb.catering.business.domain.UnknownShareLink;
import cz.vsb.catering.business.domain.User;
import cz.vsb.catering.business.domain.ValidationException;
import cz.vsb.catering.business.port.MenuMapper;
import cz.vsb.catering.business.port.ProjectMapper;
import cz.vsb.catering.business.port.ShareLinkMapper;
import cz.vsb.catering.business.port.TransactionRunner;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/** Facade for sharing with the customer and for comments (UC11, UC12, UC13). Used by both UIs.
 *  Menus of an archived project can be viewed, but not shared, commented or resolved (UC03). */
public class SharingService {
    private final MenuMapper menus;
    private final ProjectMapper projects;
    private final ShareLinkMapper links;
    private final TransactionRunner tx;
    private final Clock clock;
    private final String baseUrl;

    public SharingService(MenuMapper menus, ProjectMapper projects, ShareLinkMapper links, TransactionRunner tx,
                          Clock clock, String baseUrl) {
        this.menus = menus;
        this.projects = projects;
        this.links = links;
        this.tx = tx;
        this.clock = clock;
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }

    /** UC11 step 2: revision becomes SHARED, older active links of the menu are deactivated and a new
     *  link valid 14 days is created, all in one transaction. */
    public ShareLink shareRevision(MenuRevision revision) {
        if (!revision.isStored()) throw new ValidationException("Revizi nejprve uložte.");
        revision.validate();
        ShareLink link = tx.inTransaction(() -> {
            requireOpenProject(menus.findProjectId(revision.getMenuId()));
            MenuRevision latest = menus.findLatestHeader(revision.getMenuId()).orElseThrow();
            if (revision.getState() == RevisionState.WORKING && latest.getRevisionNo() != revision.getRevisionNo())
                throw new ConflictException(MenuService.CONFLICT);
            if (revision.getState() == RevisionState.WORKING
                    && !menus.markShared(revision.getMenuId(), revision.getRevisionNo(), revision.getLoadedSavedAt()))
                throw new ConflictException(MenuService.CONFLICT);
            for (ShareLink old : links.findByMenu(revision.getMenuId())) {
                if (old.isActive()) {
                    old.deactivate();
                    links.update(old);
                }
            }
            ShareLink created = ShareLink.create(revision.getMenuId(), revision.getRevisionNo(), now());
            links.insert(created);
            return created;
        });
        if (revision.getState() == RevisionState.WORKING) revision.markShared();   // object changes only after commit (A-08)
        return link;
    }

    public String shareUrl(ShareLink link) { return baseUrl + "/menu/" + link.getToken(); }

    /** Active and not expired link of the menu, if any (shown in the editor, UC19 check). */
    public Optional<ShareLink> findValidLink(long menuId) {
        LocalDateTime now = now();
        return tx.inTransaction(() -> links.findByMenu(menuId).stream().filter(l -> l.isValid(now)).findFirst());
    }

    /** UC11 step 5 and 5.1-5.3. Unknown tokens become the Special Case UnknownShareLink. */
    public SharedMenuView openSharedMenu(String token) {
        LocalDateTime now = now();
        return tx.inTransaction(() -> {
            ShareLink link = links.findByToken(token).orElseGet(() -> new UnknownShareLink(token));
            if (!link.exists()) return new SharedMenuView(SharedMenuView.Status.UNKNOWN, link, null, null, 0, null, false);
            if (!link.isValid(now)) {
                String current = links.findByMenu(link.getMenuId()).stream()
                        .filter(l -> l.isValid(now)).map(ShareLink::getToken).findFirst().orElse(null);
                return new SharedMenuView(SharedMenuView.Status.EXPIRED, link, null, null, 0, current, false);
            }
            MenuRevision revision = menus.findRevision(link.getMenuId(), link.getRevisionNo()).orElseThrow();
            revision.getComments();                                   // Lazy Load triggered inside the transaction
            return new SharedMenuView(SharedMenuView.Status.VALID, link, revision,
                    menus.findTitle(link.getMenuId()), menus.findGuestCount(link.getMenuId()), null,
                    projects.findById(menus.findProjectId(link.getMenuId())).map(Project::isArchived).orElse(false));
        });
    }

    /** UC11 step 7. categoryId/productId are null for a comment on the whole menu.
     *  LinkNotValidException when the link expired meanwhile. */
    public Comment addComment(String token, String authorName, String body, Long categoryId, Long productId) {
        LocalDateTime now = now();
        return tx.inTransaction(() -> {
            ShareLink link = links.findByToken(token).orElseGet(() -> new UnknownShareLink(token));
            if (!link.isValid(now)) throw new LinkNotValidException();
            requireOpenProject(menus.findProjectId(link.getMenuId()));
            if (productId != null) {
                MenuRevision revision = menus.findRevision(link.getMenuId(), link.getRevisionNo()).orElseThrow();
                boolean onRevision = revision.getCategories().stream().anyMatch(c ->
                        c.getId() != null && c.getId().equals(categoryId) && c.findItem(productId).isPresent());
                if (!onRevision) throw new ValidationException("Vybraná položka v nabídce není.");
            }
            Comment comment = Comment.create(link.getMenuId(), link.getRevisionNo(),
                    productId == null ? null : categoryId, productId, authorName, body, now);
            menus.insertComment(comment);
            return comment;
        });
    }

    /** UC13 step 2: unresolved first, then newest first. */
    public List<Comment> listComments(long menuId, int revisionNo) {
        return tx.inTransaction(() -> menus.findComments(menuId, revisionNo)).stream()
                .sorted(Comparator.comparing(Comment::isResolved)
                        .thenComparing(Comment::getCreatedAt, Comparator.reverseOrder()))
                .toList();
    }

    public long countUnresolved(long menuId, int revisionNo) {
        return listComments(menuId, revisionNo).stream().filter(c -> !c.isResolved()).count();
    }

    /** UC13 steps 4-5; ConflictException when another worker resolved it first (4.1). */
    public Comment resolveComment(long commentId, String note, User worker) {
        AccessPolicy.requireWorker(worker);
        LocalDateTime now = now();
        return tx.inTransaction(() -> {
            Comment comment = menus.findComment(commentId)
                    .orElseThrow(() -> new ValidationException("Komentář neexistuje."));
            requireOpenProject(menus.findProjectId(comment.getMenuId()));
            comment.resolve(worker.getId(), note, now);
            if (!menus.updateResolution(comment))
                throw new ConflictException("Komentář mezitím vyřídil jiný pracovník.");
            return comment;
        });
    }

    /** UC18 (actor Čas): deactivates every active link whose validity ended, in one transaction;
     *  returns how many. Run daily by presentation.web.ExpiredLinkScheduler. */
    public int deactivateExpiredLinks() {
        LocalDateTime now = now();
        return tx.inTransaction(() -> {
            int count = 0;
            for (ShareLink link : links.findActive()) {
                if (link.isValid(now)) continue;
                link.deactivate();
                links.update(link);
                count++;
            }
            return count;
        });
    }

    /** UC03: an archived project is read-only, its content tree included. */
    private void requireOpenProject(long projectId) {
        tx.inTransaction(() -> projects.findById(projectId)).ifPresent(Project::requireNotArchived);
    }

    private LocalDateTime now() { return LocalDateTime.now(clock); }
}
