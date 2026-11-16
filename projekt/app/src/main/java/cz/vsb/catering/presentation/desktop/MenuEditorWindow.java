package cz.vsb.catering.presentation.desktop;

import cz.vsb.catering.AppContext;
import cz.vsb.catering.business.command.AddCategoryCommand;
import cz.vsb.catering.business.command.AddItemCommand;
import cz.vsb.catering.business.command.ChangeItemCommand;
import cz.vsb.catering.business.command.MenuEditCommand;
import cz.vsb.catering.business.command.MenuEditor;
import cz.vsb.catering.business.command.RemoveCategoryCommand;
import cz.vsb.catering.business.command.RemoveItemCommand;
import cz.vsb.catering.business.domain.ConflictException;
import cz.vsb.catering.business.domain.Menu;
import cz.vsb.catering.business.domain.MenuCategory;
import cz.vsb.catering.business.domain.MenuItem;
import cz.vsb.catering.business.domain.MenuRevision;
import cz.vsb.catering.business.domain.MenuRevisionListener;
import cz.vsb.catering.business.domain.Project;
import cz.vsb.catering.business.domain.RevisionState;
import cz.vsb.catering.business.domain.User;
import cz.vsb.catering.business.domain.ValidationException;
import cz.vsb.catering.business.pricing.PriceStrategy;
import java.util.function.Function;
import java.util.function.IntConsumer;
import javafx.beans.property.SimpleStringProperty;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeTableColumn;
import javafx.scene.control.TreeTableRow;
import javafx.scene.control.TreeTableView;
import javafx.scene.input.KeyCombination;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.util.StringConverter;

/** UC08 menu editor. Every edit is a Command run by MenuEditor (undo = "Zpět"); the view, totals and
 *  buttons are refreshed by a MenuRevisionListener (Observer); prices use a PriceStrategy. */
class MenuEditorWindow {
    private final AppContext ctx;
    private final User user;
    private final Menu menu;
    /** UC03: the menu belongs to an archived project, nothing can be saved, shared, exported or resolved. */
    final boolean readOnly;
    private final Stage stage = new Stage();
    private final int guests;
    final ComboBox<MenuRevision> revisions = new ComboBox<>();
    final HBox banner;
    final Label bannerText = new Label();
    final Button compare = new Button("Porovnat revize"), exportPdf = new Button("Exportovat do PDF");
    /** Called after the editor added something to the project tree (PDF export), so D1 can reload. */
    Runnable onTreeChanged = () -> {};
    final Button copy = new Button("Vytvořit pracovní kopii");
    final TreeTableView<Object> table = new TreeTableView<>();
    final Label itemCount = new Label(), perGuest = new Label(), total = Ui.label("", "total");
    final ToggleButton vat = new ToggleButton("s DPH");
    final Button share = new Button("Sdílet se zákazníkem"), save = new Button("Uložit revizi");
    final Button addCategory = new Button("Přidat kategorii"), removeCategory = new Button("Odebrat kategorii");
    final Button addItem = new Button("+ Přidat položku"), editItem = new Button("Upravit položku");
    final Button removeItem = new Button("Odebrat položku"), undo = new Button("Zpět");
    final Button comments = new Button();
    private final MenuRevisionListener listener = r -> { dirty = true; refresh(); };
    private MenuRevision revision;
    private MenuEditor editor;
    private boolean dirty, switching;
    private Long highlightProductId;
    private IntConsumer afterSave;

    MenuEditorWindow(AppContext ctx, User user, Menu menu, Project project) {
        this.ctx = ctx;
        this.user = user;
        this.menu = menu;
        this.readOnly = project != null && project.isArchived();
        this.guests = ctx.menus.guestCount(menu.getMenuId());
        revisions.setConverter(new StringConverter<>() {
            @Override public String toString(MenuRevision r) {
                return r == null ? "" : "Revize " + r.getRevisionNo() + " – " + r.getState().label()
                        + (r.isStored() ? (r.getCreatedAt() == null ? "" : " · " + Ui.date(r.getCreatedAt())) : " (neuložená)");
            }
            @Override public MenuRevision fromString(String s) { return null; }
        });
        revisions.valueProperty().addListener((o, a, r) -> { if (!switching && r != null && r != revision) switchTo(r); });
        copy.setOnAction(e -> createWorkingCopy());                              // UC08 2.3-2.4
        banner = new HBox(bannerText, Ui.spacer(), copy);
        banner.getStyleClass().add("banner");

        share.getStyleClass().add("primary");
        save.getStyleClass().add("primary");
        String path = "Projekty  ›  " + (project == null ? "" : project.getName()) + "  ›  " + menu.getName();
        compare.setOnAction(e -> compare());
        exportPdf.setOnAction(e -> exportPdf());
        HBox header = new HBox(12, Ui.label(menu.getName(), "h1"), revisions, Ui.spacer(), compare, exportPdf, share);
        header.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        HBox toolbar = new HBox(8, addItem, editItem, removeItem, addCategory, removeCategory, undo);
        buildTable();
        VBox.setVgrow(table, Priority.ALWAYS);
        VBox left = new VBox(8, toolbar, table,
                Ui.label("Množství a cenu změníte tlačítkem „Upravit položku“ nebo dvojklikem. Chybné řádky se zvýrazní červeně.", "muted"));
        left.getStyleClass().add("card");
        HBox.setHgrow(left, Priority.ALWAYS);
        VBox right = new VBox(12, totalsCard(), comments);
        right.setPrefWidth(300);
        comments.setMaxWidth(Double.MAX_VALUE);
        HBox bottom = new HBox(Ui.spacer(), save);
        VBox rootBox = new VBox(10, Ui.label(path, "muted"), header, banner, new HBox(12, left, right), bottom);
        VBox.setVgrow(rootBox.getChildren().get(3), Priority.ALWAYS);
        rootBox.getStyleClass().add("content");

        addCategory.setOnAction(e -> addCategory());
        removeCategory.setOnAction(e -> { if (selectedCategory() != null) run(new RemoveCategoryCommand(revision, selectedCategory())); });
        addItem.setOnAction(e -> addItem());
        editItem.setOnAction(e -> editItem());
        removeItem.setOnAction(e -> { if (selectedItem() != null) run(new RemoveItemCommand(revision, selectedCategory(), selectedItem())); });
        undo.setOnAction(e -> editor.undo());
        vat.setOnAction(e -> refresh());
        save.setOnAction(e -> save());
        share.setOnAction(e -> share());
        comments.setOnAction(e -> openComments());
        table.getSelectionModel().selectedItemProperty().addListener((o, a, b) -> updateButtons());

        stage.setTitle("Catering IS · " + menu.getName());
        stage.setScene(Ui.scene(new BorderPane(rootBox), 1200, 720));
        stage.getScene().getAccelerators().put(KeyCombination.keyCombination("Shortcut+S"), save::fire);
        stage.getScene().getAccelerators().put(KeyCombination.keyCombination("Shortcut+Z"), undo::fire);
        stage.setOnHidden(e -> revision.removeListener(listener));
        stage.setOnCloseRequest(e -> { if (dirty && revision.isEditable() && !Ui.confirm("Neuložené změny se ztratí. Zavřít?")) e.consume(); });
        openLatest();                                                            // UC08 step 2
    }

    Stage stage() { return stage; }

    MenuRevision revision() { return revision; }

    private VBox totalsCard() {
        GridPane g = new GridPane();
        g.setHgap(20);
        g.setVgap(6);
        g.addRow(0, Ui.label("Položek", "muted"), itemCount);
        g.addRow(1, Ui.label("Na hosta (" + guests + ")", "muted"), perGuest);
        g.addRow(2, Ui.label("Celkem", "h2"), total);
        vat.setSelected(true);
        vat.selectedProperty().addListener((o, a, s) -> vat.setText(s ? "s DPH" : "bez DPH"));
        VBox card = new VBox(8, g, new HBox(8, Ui.label("Ceny:", "muted"), vat));
        card.getStyleClass().add("card");
        return card;
    }

    private void buildTable() {
        table.getColumns().add(col("Položka", 260, o -> o instanceof MenuCategory c ? c.getName() : ((MenuItem) o).getProduct().getName()));
        table.getColumns().add(col("Množství", 110, o -> o instanceof MenuItem i
                ? i.getQuantity().stripTrailingZeros().toPlainString() + " " + i.getProduct().getUnitLabel() : ""));
        table.getColumns().add(col("Jedn. cena", 110, o -> o instanceof MenuItem i ? i.getUnitPrice().format() : ""));
        table.getColumns().add(col("Cena", 120, o -> o instanceof MenuItem i ? i.price(strategy()).format()
                : ((MenuCategory) o).subtotal(strategy()).format()));
        table.getColumns().add(col("Alergeny", 100, o -> o instanceof MenuItem i ? String.join(", ", i.getProduct().getAllergens()) : ""));
        table.getColumns().add(col(MenuItemDialog.NOTE_LABEL, 160, o -> o instanceof MenuItem i && i.getNote() != null ? i.getNote() : ""));
        table.setShowRoot(false);
        table.setPlaceholder(new Label("Menu zatím nemá žádnou kategorii."));
        table.setRowFactory(t -> {
            TreeTableRow<Object> row = new TreeTableRow<>() {
                @Override protected void updateItem(Object o, boolean empty) {
                    super.updateItem(o, empty);
                    getStyleClass().removeAll("category-row", "error-row", "highlight-row");
                    if (empty || o == null) return;
                    if (o instanceof MenuCategory) getStyleClass().add("category-row");
                    else if (o instanceof MenuItem i) {
                        if (i.getQuantity().signum() <= 0 || i.getUnitPrice().isNegative()) getStyleClass().add("error-row");
                        else if (i.getProduct().getId().equals(highlightProductId)) getStyleClass().add("highlight-row");
                    }
                }
            };
            row.setOnMouseClicked(e -> { if (e.getClickCount() == 2 && row.getItem() instanceof MenuItem) editItem(); });
            return row;
        });
    }

    private static TreeTableColumn<Object, String> col(String title, double width, Function<Object, String> value) {
        TreeTableColumn<Object, String> c = new TreeTableColumn<>(title);
        c.setCellValueFactory(d -> new SimpleStringProperty(value.apply(d.getValue().getValue())));
        c.setPrefWidth(width);
        return c;
    }

    private PriceStrategy strategy() { return vat.isSelected() ? PriceStrategy.WITH_VAT : PriceStrategy.WITHOUT_VAT; }

    // ------------------------------------------------------------------ revision switching

    private void openLatest() { load(ctx.menus.openLatestRevision(menu.getMenuId())); }

    /** UC08 2.3-2.4: a refused copy (newest revision already WORKING) is a dialog, not an FX-thread exception. */
    private boolean createWorkingCopy() {
        try {
            load(ctx.menus.createWorkingCopy(revision));
            return true;
        } catch (Exception e) {
            Ui.error(e);
            return false;
        }
    }

    private void switchTo(MenuRevision header) {
        if (dirty && revision.isEditable() && !Ui.confirm("Neuložené změny se ztratí. Pokračovat?")) {
            switching = true;
            revisions.setValue(revision);
            switching = false;
            return;
        }
        try {
            load(ctx.menus.openRevision(menu.getMenuId(), header.getRevisionNo()));
        } catch (Exception e) {
            Ui.error(e);
        }
    }

    /** Shows the revision: new listener registration and a fresh undo stack (Command invoker). */
    private void load(MenuRevision r) {
        if (revision != null) revision.removeListener(listener);
        revision = r;
        editor = new MenuEditor(r);
        r.addListener(listener);
        dirty = !r.isStored();
        switching = true;
        revisions.getItems().setAll(ctx.menus.listRevisions(menu.getMenuId()));
        if (!r.isStored()) revisions.getItems().add(0, r);
        revisions.getItems().replaceAll(h -> h.getRevisionNo() == r.getRevisionNo() ? r : h);
        revisions.setValue(r);
        switching = false;
        refreshCommentCount();
        refresh();
    }

    /** Rebuilds rows, totals and buttons; called by the Observer after every change of the revision. */
    private void refresh() {
        TreeItem<Object> root = new TreeItem<>();
        for (MenuCategory c : revision.getCategories()) {
            TreeItem<Object> ci = new TreeItem<>(c);
            ci.setExpanded(true);
            c.getItems().forEach(i -> ci.getChildren().add(new TreeItem<>(i)));
            root.getChildren().add(ci);
        }
        MenuCategory category = selectedCategory();
        Object selected = table.getSelectionModel().getSelectedItem() == null ? null : table.getSelectionModel().getSelectedItem().getValue();
        table.setRoot(root);
        if (selected != null) reselect(root, selected, category);
        long count = revision.getCategories().stream().mapToLong(c -> c.getItems().size()).sum();
        itemCount.setText(String.valueOf(count));
        total.setText(revision.totalPrice(strategy()).format());
        perGuest.setText(guests > 0 ? revision.pricePerGuest(strategy(), guests).format() : "–");
        bannerText.setText(readOnly ? "Projekt je archivovaný – jen pro čtení."
                : revision.isHistorical()                                          // UC09
                ? "Starší revize – nelze měnit. Pracovní kopie se uloží jako nová revize za nejnovější."
                : "Sdílená revize – nelze měnit. Změny provedete v pracovní kopii, sdílená revize i odkaz zůstanou beze změny.");
        banner.setVisible(readOnly || !revision.isEditable());
        banner.setManaged(readOnly || !revision.isEditable());
        copy.setVisible(!readOnly);
        copy.setManaged(!readOnly);
        updateButtons();
    }

    /** A-11: the rebuilt tree selects the same object again, or its category when the item is gone. */
    private void reselect(TreeItem<Object> root, Object selected, MenuCategory category) {
        TreeItem<Object> fallback = null;
        for (TreeItem<Object> c : root.getChildren()) {
            if (c.getValue() == selected) { table.getSelectionModel().select(c); return; }
            if (c.getValue() == category) fallback = c;
            for (TreeItem<Object> i : c.getChildren())
                if (i.getValue() == selected) { table.getSelectionModel().select(i); return; }
        }
        if (fallback != null) table.getSelectionModel().select(fallback);
    }

    private void updateButtons() {
        boolean editable = !readOnly && revision != null && revision.isEditable();
        addCategory.setDisable(!editable);
        removeCategory.setDisable(!editable || selectedCategory() == null || selectedItem() != null);
        addItem.setDisable(!editable || selectedCategory() == null);
        editItem.setDisable(!editable || selectedItem() == null);
        removeItem.setDisable(!editable || selectedItem() == null);
        undo.setDisable(!editable || editor == null || !editor.canUndo());
        save.setDisable(!editable);
        share.setDisable(readOnly || revision == null || !revision.isStored() || revision.getCategories().isEmpty() || dirty);
        exportPdf.setDisable(readOnly || revision == null || !revision.isStored() || dirty);
        compare.setDisable(revisions.getItems().stream().filter(MenuRevision::isStored).count() < 2);
    }

    /** UC10: the shown revision (or the newest stored one for an unsaved copy) against the one before it;
     *  the oldest stored revision is compared with the next one. */
    CompareRevisionsWindow compare() {
        int[] stored = revisions.getItems().stream().filter(MenuRevision::isStored).mapToInt(MenuRevision::getRevisionNo).sorted().toArray();
        int shown = revision.isStored() ? revision.getRevisionNo() : stored[stored.length - 1];
        int i = java.util.Arrays.binarySearch(stored, shown);
        int older = i > 0 ? stored[i - 1] : shown, newer = i > 0 ? shown : stored[1];
        return CompareRevisionsWindow.open(ctx, menu.getMenuId(), older, newer, stage);
    }

    /** UC14: PDF of the shown stored revision into the menu's folder, with the chosen prices. */
    private void exportPdf() {
        if (readOnly || !revision.isStored() || dirty) return;
        MenuPdfExport.exportAndReport(ctx, menu, revision.getRevisionNo(), strategy()).ifPresent(d -> onTreeChanged.run());
    }

    void refreshCommentCount() {
        long n = ctx.sharing.countUnresolved(menu.getMenuId(), commentsRevisionNo());
        comments.setText("Komentáře k rev. " + commentsRevisionNo() + " (" + n + " nevyřízených)");
    }

    /** Customers comment only shared revisions: for a working revision the comments of the newest
     *  older shared revision are the ones being processed (UC13 3.2-3.5). */
    private int commentsRevisionNo() {
        if (revision.getState() == RevisionState.SHARED) return revision.getRevisionNo();
        return revisions.getItems().stream().filter(r -> r.getState() == RevisionState.SHARED)
                .mapToInt(MenuRevision::getRevisionNo).filter(no -> no < revision.getRevisionNo())
                .max().orElse(revision.getRevisionNo());
    }

    // ------------------------------------------------------------------ edits (Command pattern)

    private void run(MenuEditCommand command) {
        try {
            editor.execute(command);
        } catch (ValidationException e) {
            Ui.error(e);
        }
    }

    private MenuCategory selectedCategory() {
        TreeItem<Object> sel = table.getSelectionModel().getSelectedItem();
        if (sel == null) return null;
        return sel.getValue() instanceof MenuCategory c ? c : (MenuCategory) sel.getParent().getValue();
    }

    private MenuItem selectedItem() {
        TreeItem<Object> sel = table.getSelectionModel().getSelectedItem();
        return sel != null && sel.getValue() instanceof MenuItem i ? i : null;
    }

    private void addCategory() {
        Ui.prompter.text("Přidat kategorii", "Název kategorie:", "").ifPresent(name -> run(new AddCategoryCommand(revision, name)));
    }

    private void addItem() {
        MenuCategory category = selectedCategory();
        if (category == null) return;
        Ui.prompter.addItem(ctx.menus.findProducts(), guests).ifPresent(v ->
                run(new AddItemCommand(revision, category, v.product(), v.quantity(), v.unitPrice())));
    }

    private void editItem() {
        MenuItem item = selectedItem();
        if (item == null || readOnly || !revision.isEditable()) return;
        Ui.prompter.editItem(item).ifPresent(v -> run(new ChangeItemCommand(revision, item, v.quantity(), v.unitPrice(), v.note())));
    }

    // ------------------------------------------------------------------ save, share, comments

    /** UC08 steps 5-8, 6.1-6.3 and 7.1-7.3. */
    private void save() {
        if (readOnly || !revision.isEditable()) return;
        var note = Ui.prompter.text("Uložit revizi", "Poznámka k revizi:", "");
        if (note.isEmpty()) return;
        try {
            int no = ctx.menus.saveRevision(revision, note.get(), user);
            load(revision);
            Ui.info("Uloženo jako revize č. " + no + ".");
            if (afterSave != null) {                                            // UC13 3.4
                IntConsumer callback = afterSave;
                afterSave = null;
                callback.accept(no);
            }
        } catch (ConflictException e) {
            Ui.error(e);
            openLatest();
        } catch (ValidationException e) {
            Ui.warn("Menu obsahuje chyby a nebylo uloženo:\n" + e.getMessage());
            table.refresh();
        } catch (Exception e) {
            Ui.error(e);
        }
    }

    /** UC11 steps 1-3. */
    private void share() {
        try {
            var link = ctx.sharing.shareRevision(revision);
            load(revision);
            Ui.prompter.show(new ShareDialog(ctx.sharing, link, menu.getName()));
        } catch (ConflictException e) {
            Ui.error(e);
            openLatest();
        } catch (Exception e) {
            Ui.error(e);
        }
    }

    CommentsWindow openComments() {
        CommentsWindow w = new CommentsWindow(ctx.sharing, user, this, menu.getMenuId(), commentsRevisionNo(), menu.getName());
        w.stage().initOwner(stage);
        w.stage().addEventHandler(javafx.stage.WindowEvent.WINDOW_HIDDEN, e -> refreshCommentCount());                       // UC13 7.2
        w.stage().show();
        return w;
    }

    /** UC13 3.2: latest revision (working copy of a shared one), the commented product highlighted;
     *  after the next save afterSave gets the revision number. */
    void editForComment(Long productId, IntConsumer afterSave) {
        if (readOnly) return;
        if (!revision.isEditable()) {
            openLatest();
            if (!revision.isEditable() && !createWorkingCopy()) return;
        }
        highlightProductId = productId;
        this.afterSave = afterSave;
        table.refresh();
        for (TreeItem<Object> c : table.getRoot().getChildren())
            for (TreeItem<Object> i : c.getChildren())
                if (((MenuItem) i.getValue()).getProduct().getId().equals(productId)) table.getSelectionModel().select(i);
        stage.toFront();
    }
}
