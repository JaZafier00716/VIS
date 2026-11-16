package cz.vsb.catering.presentation.desktop;

import cz.vsb.catering.AppContext;
import cz.vsb.catering.business.domain.MenuItem;
import cz.vsb.catering.business.domain.MenuRevision;
import cz.vsb.catering.business.domain.Money;
import cz.vsb.catering.business.domain.RevisionComparison;
import cz.vsb.catering.business.domain.RevisionComparison.Row;
import cz.vsb.catering.business.pricing.PriceStrategy;
import java.util.function.Function;
import javafx.beans.property.SimpleStringProperty;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.util.StringConverter;

/** UC10: one table of differences between two revisions of a menu; added rows green, removed red,
 *  changed amber. Totals are with VAT like the editor default. */
class CompareRevisionsWindow {
    private static final PriceStrategy PRICES = PriceStrategy.WITH_VAT;
    private final AppContext ctx;
    private final long menuId;
    private final Stage stage = new Stage();
    final ComboBox<MenuRevision> older = new ComboBox<>(), newer = new ComboBox<>();
    final CheckBox onlyChanges = new CheckBox("jen změny");
    final TableView<Row> table = new TableView<>();
    final Label totals = Ui.label(""), difference = Ui.label("", "total");
    private RevisionComparison comparison;

    CompareRevisionsWindow(AppContext ctx, long menuId, int olderNo, int newerNo) {
        this.ctx = ctx;
        this.menuId = menuId;
        String title = ctx.menus.title(menuId);
        for (ComboBox<MenuRevision> box : java.util.List.of(older, newer)) {
            box.getItems().setAll(ctx.menus.listRevisions(menuId));
            box.setConverter(new StringConverter<>() {
                @Override public String toString(MenuRevision r) {
                    return r == null ? "" : "rev. " + r.getRevisionNo() + " – " + r.getState().label()
                            + (r.getCreatedAt() == null ? "" : " · " + Ui.date(r.getCreatedAt()));
                }
                @Override public MenuRevision fromString(String s) { return null; }
            });
        }
        select(older, olderNo);
        select(newer, newerNo);
        Button compare = new Button("Porovnat");
        compare.getStyleClass().add("primary");
        compare.setOnAction(e -> compare());
        onlyChanges.setOnAction(e -> fill());
        Button close = new Button("Zavřít");
        close.setOnAction(e -> stage.hide());

        HBox selectors = new HBox(8, Ui.label("Starší revize:", "muted"), older, Ui.label("Novější revize:", "muted"), newer,
                compare, Ui.spacer(), onlyChanges);
        selectors.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        buildTable();
        VBox.setVgrow(table, Priority.ALWAYS);
        HBox bottom = new HBox(16, new VBox(2, totals, difference), Ui.spacer(), close);
        bottom.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        VBox root = new VBox(10, Ui.label("Porovnání revizí – " + title, "h1"), selectors, table, bottom);
        root.getStyleClass().add("content");
        stage.setTitle("Catering IS · Porovnání revizí");
        stage.setScene(Ui.scene(new BorderPane(root), 1140, 620));
        compare();
    }

    /** Entry point for the menu editor ("Porovnat revize"). */
    static CompareRevisionsWindow open(AppContext ctx, long menuId, int olderNo, int newerNo) {
        return open(ctx, menuId, olderNo, newerNo, null);
    }

    static CompareRevisionsWindow open(AppContext ctx, long menuId, int olderNo, int newerNo, javafx.stage.Window owner) {
        CompareRevisionsWindow w = new CompareRevisionsWindow(ctx, menuId, olderNo, newerNo);
        if (owner != null) w.stage.initOwner(owner);
        w.stage.show();
        return w;
    }

    Stage stage() { return stage; }

    RevisionComparison comparison() { return comparison; }

    private static void select(ComboBox<MenuRevision> box, int revisionNo) {
        box.getItems().stream().filter(r -> r.getRevisionNo() == revisionNo).findFirst().ifPresent(box::setValue);
    }

    void compare() {
        if (older.getValue() == null || newer.getValue() == null) return;
        try {
            comparison = ctx.menus.compareRevisions(menuId, older.getValue().getRevisionNo(), newer.getValue().getRevisionNo());
        } catch (Exception e) {
            Ui.error(e);
            return;
        }
        fill();
        int a = comparison.getOlder().getRevisionNo(), b = comparison.getNewer().getRevisionNo();
        totals.setText("Celkem s DPH: rev. " + a + " " + comparison.olderTotal(PRICES).format()
                + " · rev. " + b + " " + comparison.newerTotal(PRICES).format()
                + " · změn: " + comparison.changes().size());
        Money diff = comparison.difference(PRICES);
        difference.setText("Rozdíl celkové ceny: " + (diff.isNegative() ? "" : "+ ") + diff.format());
    }

    private void fill() {
        if (comparison == null) return;
        table.getItems().setAll(onlyChanges.isSelected() ? comparison.changes() : comparison.getRows());
    }

    private void buildTable() {
        table.getColumns().add(col("Změna", 215, r -> r.status().label()
                + (r.changes().isEmpty() ? "" : " (" + String.join(", ", r.changes()) + ")")));
        table.getColumns().add(col("Kategorie", 110, Row::category));
        table.getColumns().add(col("Položka", 220, r -> r.product().getName()));
        table.getColumns().add(col("Původně", 250, r -> describe(r.left())));
        table.getColumns().add(col("Nově", 280, r -> describe(r.right())));
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.setPlaceholder(new Label("Obě revize jsou prázdné."));
        table.setRowFactory(t -> new TableRow<>() {
            @Override protected void updateItem(Row r, boolean empty) {
                super.updateItem(r, empty);
                getStyleClass().removeAll("added-row", "removed-row", "changed-row");
                if (empty || r == null) return;
                switch (r.status()) {
                    case ADDED -> getStyleClass().add("added-row");
                    case REMOVED -> getStyleClass().add("removed-row");
                    case CHANGED -> getStyleClass().add("changed-row");
                    default -> { }
                }
            }
        });
    }

    /** e.g. "120 ks × 70,00 Kč · Navýšeno"; "–" when the item is not in that revision. */
    private static String describe(MenuItem i) {
        if (i == null) return "–";
        return i.getQuantity().stripTrailingZeros().toPlainString() + " " + i.getProduct().getUnitLabel() + " × "
                + i.getUnitPrice().format() + (i.getNote() == null ? "" : " · " + i.getNote());
    }

    private static TableColumn<Row, String> col(String title, double width, Function<Row, String> value) {
        TableColumn<Row, String> c = new TableColumn<>(title);
        c.setCellValueFactory(d -> new SimpleStringProperty(value.apply(d.getValue())));
        c.setPrefWidth(width);
        return c;
    }
}
