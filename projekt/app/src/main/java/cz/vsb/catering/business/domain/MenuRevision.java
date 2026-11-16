package cz.vsb.catering.business.domain;

import cz.vsb.catering.business.pricing.PriceStrategy;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

/** Revision of a menu (UC08). A WORKING revision can be edited and is overwritten on save;
 *  a SHARED revision is read-only and edits go to a working copy with number + 1.
 *  Subject of the Observer pattern: listeners are told about every successful change. */
public class MenuRevision {
    public static final int MAX_CATEGORY_NAME = 60;
    public static final int MAX_ITEM_NOTE = 200;
    private static final BigDecimal MAX_QUANTITY = new BigDecimal("1000000");      // NUMBER(8,2)
    private static final BigDecimal MAX_UNIT_PRICE = new BigDecimal("100000000");  // NUMBER(10,2)
    private final long menuId;
    private final int revisionNo;
    private RevisionState state;
    private String note;
    private Long createdBy;
    private LocalDateTime createdAt;
    private boolean stored;
    private Integer latestRevisionNo;          // set when a newer revision exists (UC09)
    private LocalDateTime loadedSavedAt;
    private final List<MenuCategory> categories = new ArrayList<>();
    private final List<MenuRevisionListener> listeners = new ArrayList<>();
    private Supplier<List<Comment>> commentLoader = List::of;
    private List<Comment> comments;

    public MenuRevision(long menuId, int revisionNo, RevisionState state, String note,
                        Long createdBy, LocalDateTime createdAt, boolean stored) {
        this.menuId = menuId;
        this.revisionNo = revisionNo;
        this.state = state;
        this.note = note;
        this.createdBy = createdBy;
        this.createdAt = createdAt;
        this.stored = stored;
        this.loadedSavedAt = createdAt;
    }

    /** First, empty revision of a new menu (UC02 step 9, UC07). */
    public static MenuRevision first(long menuId) {
        return new MenuRevision(menuId, 1, RevisionState.WORKING, null, null, null, false);
    }

    // ---------------------------------------------------------------- editing (UC08 steps 3-4)

    public MenuCategory addCategory(String name) {
        requireEditable();
        String trimmed = name == null ? "" : name.trim();
        if (trimmed.isEmpty()) throw new ValidationException("Název kategorie je povinný.");
        if (trimmed.length() > MAX_CATEGORY_NAME)
            throw new ValidationException("Název kategorie má nejvýše " + MAX_CATEGORY_NAME + " znaků.");
        if (categories.stream().anyMatch(c -> c.getName().equalsIgnoreCase(trimmed)))
            throw new ValidationException("Kategorie „" + trimmed + "“ v menu již existuje.");
        MenuCategory category = new MenuCategory(trimmed);
        categories.add(category);
        changed();
        return category;
    }

    /** Puts a removed category back at its position (undo of RemoveCategoryCommand). */
    public void insertCategory(int index, MenuCategory category) {
        requireEditable();
        categories.add(index, category);
        changed();
    }

    /** Removes the category and returns its former index. */
    public int removeCategory(MenuCategory category) {
        requireEditable();
        int index = categories.indexOf(category);
        if (index < 0) throw new IllegalArgumentException("Kategorie v této revizi není.");
        categories.remove(index);
        changed();
        return index;
    }

    public MenuItem addItem(MenuCategory category, Product product, BigDecimal quantity, Money unitPrice, String note) {
        requireEditable();
        requireOwn(category);
        requireAmounts(quantity, unitPrice);
        MenuItem item = new MenuItem(product, quantity, unitPrice, note);
        category.addItem(item);
        changed();
        return item;
    }

    public void insertItem(MenuCategory category, int index, MenuItem item) {
        requireEditable();
        requireOwn(category);
        category.insertItem(index, item);
        changed();
    }

    public void changeItem(MenuItem item, BigDecimal quantity, Money unitPrice, String note) {
        requireEditable();
        requireAmounts(quantity, unitPrice);
        item.change(quantity, unitPrice, note);
        changed();
    }

    /** Removes the item and returns its former index in the category. */
    public int removeItem(MenuCategory category, MenuItem item) {
        requireEditable();
        requireOwn(category);
        int index = category.getItems().indexOf(item);
        if (index < 0) throw new IllegalArgumentException("Položka v této kategorii není.");
        category.removeItem(item);
        changed();
        return index;
    }

    // ---------------------------------------------------------------- rules

    /** Only the newest revision can be edited, and only while it is WORKING. */
    public boolean isEditable() { return state == RevisionState.WORKING && latestRevisionNo == null; }

    /** UC09: an older revision is shown read-only; its working copy gets the number after the newest one. */
    public void markHistorical(int latestRevisionNo) {
        if (latestRevisionNo <= revisionNo) throw new IllegalArgumentException("Revize není starší než nejnovější.");
        this.latestRevisionNo = latestRevisionNo;
    }

    public boolean isHistorical() { return latestRevisionNo != null; }

    public Money totalPrice(PriceStrategy strategy) {
        return categories.stream().map(c -> c.subtotal(strategy)).reduce(Money.ZERO, Money::plus);
    }

    public Money pricePerGuest(PriceStrategy strategy, int guestCount) {
        return totalPrice(strategy).dividedBy(guestCount);
    }

    /** UC08 step 6 checks; returns Czech messages, empty when the menu is valid. */
    public List<String> findProblems() {
        List<String> problems = new ArrayList<>();
        if (categories.isEmpty()) problems.add("Menu musí obsahovat alespoň jednu kategorii.");
        for (MenuCategory c : categories) {
            if (c.getName().length() > MAX_CATEGORY_NAME)
                problems.add(c.getName() + ": název kategorie má nejvýše " + MAX_CATEGORY_NAME + " znaků.");
            Set<Long> products = new HashSet<>();
            for (MenuItem i : c.getItems()) {
                String where = c.getName() + " / " + i.getProduct().getName();
                if (i.getQuantity() == null || i.getQuantity().signum() <= 0)
                    problems.add(where + ": množství musí být větší než 0.");
                else if (i.getQuantity().stripTrailingZeros().scale() > 2 || i.getQuantity().compareTo(MAX_QUANTITY) >= 0)
                    problems.add(where + ": množství má nejvýše 2 desetinná místa a je menší než 1 000 000.");
                if (i.getUnitPrice() == null || i.getUnitPrice().isNegative())
                    problems.add(where + ": cena nesmí být záporná.");
                else if (i.getUnitPrice() != null && i.getUnitPrice().amount().compareTo(MAX_UNIT_PRICE) >= 0)
                    problems.add(where + ": cena musí být menší než 100 000 000.");
                if (i.getNote() != null && i.getNote().length() > MAX_ITEM_NOTE)
                    problems.add(where + ": poznámka má nejvýše " + MAX_ITEM_NOTE + " znaků.");
                if (!products.add(i.getProduct().getId()))
                    problems.add(where + ": produkt je v kategorii dvakrát.");
            }
        }
        return problems;
    }

    public void validate() {
        List<String> problems = findProblems();
        if (!problems.isEmpty()) throw new ValidationException(String.join("\n", problems), Map.of("menu", problems.get(0)));
    }

    /** UC08 2.4: unsaved copy with number + 1 (after the newest revision for an older one) that can be edited; this revision stays unchanged. */
    public MenuRevision createWorkingCopy() {
        if (isEditable()) throw new IllegalStateException("Pracovní revizi lze upravovat přímo.");
        int number = (latestRevisionNo == null ? revisionNo : latestRevisionNo) + 1;
        MenuRevision copy = new MenuRevision(menuId, number, RevisionState.WORKING, null, null, null, false);
        categories.forEach(c -> copy.categories.add(c.copy()));
        return copy;
    }

    /** UC11 step 2: only a valid revision is shared; afterwards it is read-only. */
    public void markShared() {
        validate();
        state = RevisionState.SHARED;
        changed();
    }

    /** Called by MenuService just before the revision is written: who saved it, when and why. */
    public void recordSave(String note, long userId, LocalDateTime savedAt) {
        this.note = note;
        this.createdBy = userId;
        this.createdAt = savedAt == null ? null : savedAt.truncatedTo(java.time.temporal.ChronoUnit.MICROS); // DB keeps microseconds
    }

    /** Same revision header with the save data filled in, for the mapper to write before this object is changed (A-08).
     *  Categories are shared, so ids assigned by the mapper land on this revision's categories. */
    public MenuRevision snapshotForSave(String note, long userId, LocalDateTime savedAt) {
        MenuRevision snapshot = new MenuRevision(menuId, revisionNo, state, note, userId,
                savedAt.truncatedTo(java.time.temporal.ChronoUnit.MICROS), stored);
        snapshot.loadedSavedAt = loadedSavedAt;
        snapshot.categories.addAll(categories);
        return snapshot;
    }

    /** Called by MenuService after the transaction committed. */
    public void markStored() {
        this.stored = true;
        this.loadedSavedAt = createdAt;
    }

    /** Save time as read from the database; the optimistic check of UC08 7.1 compares it on overwrite. */
    public LocalDateTime getLoadedSavedAt() { return loadedSavedAt; }

    // ---------------------------------------------------------------- Lazy Load of comments

    /** The mapper gives a loader; comments are read from the database on the first getComments(). */
    public void setCommentLoader(Supplier<List<Comment>> loader) {
        this.commentLoader = loader;
        this.comments = null;
    }

    public List<Comment> getComments() {
        if (comments == null) comments = new ArrayList<>(commentLoader.get());
        return Collections.unmodifiableList(comments);
    }

    public boolean areCommentsLoaded() { return comments != null; }

    // ---------------------------------------------------------------- Observer

    public void addListener(MenuRevisionListener listener) { listeners.add(listener); }

    public void removeListener(MenuRevisionListener listener) { listeners.remove(listener); }

    private void changed() {
        for (MenuRevisionListener l : List.copyOf(listeners)) l.revisionChanged(this);
    }

    private void requireEditable() {
        if (!isEditable()) throw new ValidationException((isHistorical() ? "Starší" : "Sdílenou")
                + " revizi nelze měnit. Vytvořte pracovní kopii.");
    }

    private static void requireAmounts(BigDecimal quantity, Money unitPrice) {
        if (quantity == null) throw new ValidationException("Množství je povinné.");
        if (unitPrice == null) throw new ValidationException("Cena je povinná.");
    }

    private void requireOwn(MenuCategory category) {
        if (!categories.contains(category)) throw new IllegalArgumentException("Kategorie v této revizi není.");
    }

    public long getMenuId() { return menuId; }
    public int getRevisionNo() { return revisionNo; }
    public RevisionState getState() { return state; }
    public String getNote() { return note; }
    public Long getCreatedBy() { return createdBy; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    /** false for a new menu's first revision before save and for an unsaved working copy. */
    public boolean isStored() { return stored; }
    public List<MenuCategory> getCategories() { return Collections.unmodifiableList(categories); }

    /** Used by the mapper when loading. */
    public void loadCategory(MenuCategory category) { categories.add(category); }

    @Override public String toString() { return "Revize " + revisionNo + " (" + state.label() + ")"; }
}
