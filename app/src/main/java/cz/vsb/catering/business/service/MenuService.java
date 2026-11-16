package cz.vsb.catering.business.service;

import cz.vsb.catering.business.domain.AccessPolicy;
import cz.vsb.catering.business.domain.ConflictException;
import cz.vsb.catering.business.domain.Folder;
import cz.vsb.catering.business.domain.Menu;
import cz.vsb.catering.business.domain.MenuRevision;
import cz.vsb.catering.business.domain.NodeCreator;
import cz.vsb.catering.business.domain.Product;
import cz.vsb.catering.business.domain.Project;
import cz.vsb.catering.business.domain.RevisionComparison;
import cz.vsb.catering.business.domain.RevisionState;
import cz.vsb.catering.business.domain.User;
import cz.vsb.catering.business.domain.ValidationException;
import cz.vsb.catering.business.port.MenuMapper;
import cz.vsb.catering.business.port.ProductMapper;
import cz.vsb.catering.business.port.ProjectMapper;
import cz.vsb.catering.business.port.ProjectNodeMapper;
import cz.vsb.catering.business.port.TransactionRunner;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/** Facade for menus and revisions (UC02 steps 8-9 / UC07, UC08, UC09 older revisions, UC10 comparison).
 *  Menus of an archived project can be opened and compared, but not created or saved (UC03). */
public class MenuService {
    public static final int MAX_NOTE = 500;
    static final String CONFLICT = "Menu mezitím změnil jiný pracovník. Načítám nejnovější revizi.";

    static final String WORKING_EXISTS = "Nejnovější revize je pracovní. Upravte ji, nebo ji nejprve sdílejte.";

    private final MenuMapper menus;
    private final ProjectMapper projects;
    private final ProjectNodeMapper nodes;
    private final ProductMapper products;
    private final TransactionRunner tx;
    private final Clock clock;

    public MenuService(MenuMapper menus, ProjectMapper projects, ProjectNodeMapper nodes, ProductMapper products,
                       TransactionRunner tx, Clock clock) {
        this.menus = menus;
        this.projects = projects;
        this.nodes = nodes;
        this.products = products;
        this.tx = tx;
        this.clock = clock;
    }

    /** UC02 steps 8-9: MENU node in the folder plus the first empty WORKING revision, in one transaction. */
    public Menu createMenu(Folder parent, String name, User worker) {
        AccessPolicy.requireWorker(worker);
        Menu menu = (Menu) new NodeCreator.MenuCreator().createIn(parent, name);
        try {
            tx.inTransaction(() -> {
                requireOpenProject(parent.getProjectId());
                nodes.insert(menu);
                menus.insertMenu(menu, menu.getProjectId());
                MenuRevision first = MenuRevision.first(menu.getMenuId());
                first.recordSave(null, worker.getId(), now());
                menus.insertRevision(first);
            });
        } catch (RuntimeException e) {
            parent.remove(menu);
            throw e;
        }
        return menu;
    }

    /** UC08 step 2 (and 7.3): newest revision with categories and items. */
    public MenuRevision openLatestRevision(long menuId) {
        return tx.inTransaction(() -> {
            MenuRevision latest = menus.findLatestHeader(menuId)
                    .orElseThrow(() -> new ValidationException("Menu nemá žádnou revizi."));
            return menus.findRevision(menuId, latest.getRevisionNo()).orElseThrow();
        });
    }

    /** UC09: any revision; one older than the newest is read-only (its working copy follows the newest). */
    public MenuRevision openRevision(long menuId, int revisionNo) {
        return tx.inTransaction(() -> {
            MenuRevision revision = findRevision(menuId, revisionNo);
            int latest = menus.findLatestHeader(menuId).orElseThrow().getRevisionNo();
            if (latest > revisionNo) revision.markHistorical(latest);
            return revision;
        });
    }

    /** UC10: differences between two stored revisions, the lower number is taken as the older one. */
    public RevisionComparison compareRevisions(long menuId, int a, int b) {
        return tx.inTransaction(() -> new RevisionComparison(
                findRevision(menuId, Math.min(a, b)), findRevision(menuId, Math.max(a, b))));
    }

    private MenuRevision findRevision(long menuId, int revisionNo) {
        return menus.findRevision(menuId, revisionNo)
                .orElseThrow(() -> new ValidationException("Revize " + revisionNo + " neexistuje."));
    }

    /** Headers of all revisions, newest first (revision list in the editor). */
    public List<MenuRevision> listRevisions(long menuId) {
        return tx.inTransaction(() -> menus.findRevisionHeaders(menuId));
    }

    /** UC08 step 3: catalogue for the product picker. */
    public List<Product> findProducts() { return tx.inTransaction(products::findAll); }

    /** UC08 2.4: unsaved copy of a SHARED revision with number + 1; refused while the newest revision is WORKING. */
    public MenuRevision createWorkingCopy(MenuRevision shared) {
        MenuRevision latest = tx.inTransaction(() -> menus.findLatestHeader(shared.getMenuId()).orElseThrow());
        if (latest.getState() == RevisionState.WORKING) throw new ConflictException(WORKING_EXISTS);
        return shared.createWorkingCopy();
    }

    /** UC08 steps 6-7. A stored WORKING revision is overwritten (its number stays), a working copy
     *  is inserted as a new revision. ConflictException (7.1) when the menu changed in the meantime. */
    public int saveRevision(MenuRevision revision, String note, User worker) {
        AccessPolicy.requireWorker(worker);
        revision.validate();
        if (note != null && note.trim().length() > MAX_NOTE) {
            String msg = "Poznámka má nejvýše " + MAX_NOTE + " znaků.";
            throw new ValidationException(msg, Map.of("note", msg));
        }
        String cleanNote = note == null || note.isBlank() ? null : note.trim();
        LocalDateTime savedAt = now();
        MenuRevision toSave = revision.snapshotForSave(cleanNote, worker.getId(), savedAt);   // revision itself changes after commit (A-08)
        tx.inTransaction(() -> {
            requireOpenProject(menus.findProjectId(revision.getMenuId()));
            MenuRevision latest = menus.findLatestHeader(revision.getMenuId()).orElseThrow();
            if (revision.isStored()) {
                if (latest.getRevisionNo() != revision.getRevisionNo() || latest.getState() != RevisionState.WORKING)
                    throw new ConflictException(CONFLICT);
                if (!menus.overwriteRevision(toSave)) throw new ConflictException(CONFLICT);
            } else {
                if (latest.getRevisionNo() != revision.getRevisionNo() - 1)
                    throw new ConflictException(CONFLICT);
                if (latest.getState() != RevisionState.SHARED) throw new ConflictException(WORKING_EXISTS);
                menus.insertRevision(toSave);
            }
        });
        revision.recordSave(cleanNote, worker.getId(), savedAt);
        revision.markStored();
        return revision.getRevisionNo();
    }

    /** Guest count of the menu's project (price per guest, UC08 step 4). */
    /** A-17 (UC15): id of the project that owns the menu, to authorize a manager's page; unknown menu = ValidationException. */
    public long projectIdOf(long menuId) {
        try {
            return tx.inTransaction(() -> menus.findProjectId(menuId));
        } catch (IllegalStateException e) {
            throw new ValidationException("Menu neexistuje.");
        }
    }

    public int guestCount(long menuId) { return tx.inTransaction(() -> menus.findGuestCount(menuId)); }

    public String title(long menuId) { return tx.inTransaction(() -> menus.findTitle(menuId)); }

    /** UC03: an archived project is read-only, its content tree included. */
    private void requireOpenProject(long projectId) {
        tx.inTransaction(() -> projects.findById(projectId)).ifPresent(Project::requireNotArchived);
    }

    private LocalDateTime now() { return LocalDateTime.now(clock); }
}
