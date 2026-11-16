package cz.vsb.catering.presentation.desktop;

import cz.vsb.catering.business.domain.Allergen;
import cz.vsb.catering.business.domain.Product;
import cz.vsb.catering.business.domain.ValidationException;
import cz.vsb.catering.business.service.CatalogService;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import javafx.beans.property.SimpleStringProperty;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.util.StringConverter;

/** UC06 Spravovat katalog produktů: searchable list and a form with allergens and a photo. */
class CatalogWindow {
    /** Czech labels of the unit codes stored in products.unit. */
    static final Map<String, String> UNITS = new LinkedHashMap<>();
    static {
        for (String[] u : new String[][]{{"pcs", "ks"}, {"portion", "porce"}, {"kg", "kg"}, {"l", "l"}, {"set", "sada"}})
            UNITS.put(u[0], u[1]);
    }

    private final CatalogService catalog;
    private final Stage stage = new Stage();
    private final List<Product> all = new ArrayList<>();
    final TableView<Product> table = new TableView<>();
    final TextField search = new TextField();
    private final Label formTitle = Ui.label("", "h2");
    final TextField name = new TextField(), price = new TextField();
    final TextArea description = new TextArea();
    final ComboBox<String> unit = new ComboBox<>();
    final ComboBox<BigDecimal> vat = new ComboBox<>();
    final Map<Integer, CheckBox> allergens = new LinkedHashMap<>();
    private final List<Allergen> allergenList;
    final ImageView photoView = new ImageView();
    private final Label photoInfo = Ui.label("", "muted"), history = Ui.label("", "muted");
    private final Ui.FieldErrors errors = new Ui.FieldErrors();
    final Button newProduct = new Button("+ Nový produkt"), choosePhoto = new Button("Vybrat fotografii…"),
            save = new Button("Uložit"), delete = new Button("Smazat");
    private Product editing;                                           // null = new product
    private byte[] newPhoto;                                           // chosen but not saved yet

    CatalogWindow(CatalogService catalog) {
        this.catalog = catalog;
        table.getColumns().add(col("Název", 230, Product::getName));
        table.getColumns().add(col("Jednotka", 70, p -> p.getUnitLabel()));
        table.getColumns().add(col("Cena bez DPH", 110, p -> p.getCurrentPrice().format()));
        table.getColumns().add(col("DPH", 50, p -> p.getVatRate().stripTrailingZeros().toPlainString() + " %"));
        table.getColumns().add(col("Alergeny", 110, p -> p.getAllergenList().stream()
                .map(a -> String.valueOf(a.id())).collect(Collectors.joining(", "))));
        table.getSelectionModel().selectedItemProperty().addListener((o, a, p) -> { if (p != null) fill(p); });
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.setPlaceholder(new Label("Žádný produkt neodpovídá hledání."));
        VBox.setVgrow(table, Priority.ALWAYS);
        search.setPromptText("Hledat podle názvu");
        search.textProperty().addListener((o, a, t) -> filter());
        newProduct.setOnAction(e -> { table.getSelectionModel().clearSelection(); fill(null); });
        VBox left = new VBox(10, new HBox(Ui.label("Katalog produktů", "h1"), Ui.spacer(), newProduct), search, table);
        left.getStyleClass().add("content");

        unit.getItems().addAll(UNITS.keySet());
        unit.setConverter(converter(u -> u == null ? "" : UNITS.get(u)));
        vat.getItems().addAll(BigDecimal.ZERO, new BigDecimal("12"), new BigDecimal("21"));
        vat.setConverter(converter(v -> v == null ? "" : v.stripTrailingZeros().toPlainString() + " %"));
        description.setPrefRowCount(3);
        description.setWrapText(true);
        GridPane allergenGrid = new GridPane();
        allergenGrid.setHgap(10);
        allergenGrid.setVgap(3);
        allergenList = load(catalog::findAllergens);
        for (Allergen a : allergenList) {
            CheckBox box = new CheckBox(a.id() + " " + a.name());
            allergens.put(a.id(), box);
            allergenGrid.add(box, (a.id() - 1) / 7, (a.id() - 1) % 7);
        }
        photoView.setFitWidth(110);
        photoView.setFitHeight(80);
        photoView.setPreserveRatio(true);
        choosePhoto.setOnAction(e -> choosePhoto());
        HBox photoBox = new HBox(10, photoView, new VBox(6, choosePhoto, photoInfo));

        save.getStyleClass().add("primary");
        save.setOnAction(e -> save());
        delete.getStyleClass().add("danger");
        delete.setGraphic(Ui.icon(Ui.TRASH));
        delete.setOnAction(e -> delete());
        VBox form = new VBox(6, formTitle,
                new Label("Název *"), errors.add("name", name),
                new Label("Popis"), errors.add("description", description),
                new HBox(8, new VBox(4, new Label("Jednotka *"), errors.add("unit", unit)),
                        new VBox(4, new Label("Cena bez DPH (Kč) *"), errors.add("price", price)),
                        new VBox(4, new Label("DPH *"), errors.add("vatRate", vat))),
                new Label("Alergeny"), allergenGrid,
                new Label("Fotografie (nejvýše 100 kB)"), errors.add("photo", photoBox),
                history,
                new HBox(8, delete, Ui.spacer(), save));
        form.getStyleClass().add("card");
        form.setPrefWidth(460);
        ScrollPane right = new ScrollPane(new VBox(form));
        right.setFitToWidth(true);
        right.getContent().getStyleClass().add("content");

        stage.setTitle("Catering IS · Katalog produktů");
        stage.setScene(Ui.scene(new BorderPane(left, null, right, null, null), 1200, 760));
        reload(null);
        fill(null);
    }

    Stage stage() { return stage; }

    private static TableColumn<Product, String> col(String title, double width, Function<Product, String> value) {
        TableColumn<Product, String> c = new TableColumn<>(title);
        c.setCellValueFactory(d -> new SimpleStringProperty(value.apply(d.getValue())));
        c.setPrefWidth(width);
        return c;
    }

    private static <T> StringConverter<T> converter(Function<T, String> toText) {
        return new StringConverter<>() {
            @Override public String toString(T value) { return toText.apply(value); }
            @Override public T fromString(String s) { return null; }      // combo boxes are not editable
        };
    }

    private static <T> List<T> load(java.util.function.Supplier<List<T>> query) {
        try {
            return query.get();
        } catch (Exception e) {
            Ui.error(e);
            return List.of();
        }
    }

    private void reload(Long selectId) {
        all.clear();
        all.addAll(load(catalog::findProducts));
        filter();
        table.getItems().stream().filter(p -> p.getId().equals(selectId)).findFirst()
                .ifPresent(table.getSelectionModel()::select);
    }

    private void filter() {
        String t = search.getText().trim().toLowerCase(Locale.ROOT);
        table.getItems().setAll(all.stream().filter(p -> p.getName().toLowerCase(Locale.ROOT).contains(t)).toList());
    }

    private void fill(Product p) {
        editing = p;
        newPhoto = null;
        errors.clear();
        formTitle.setText(p == null ? "Nový produkt" : "Upravit produkt");
        name.setText(p == null ? "" : p.getName());
        description.setText(p == null || p.getDescription() == null ? "" : p.getDescription());
        unit.setValue(p == null ? "portion" : p.getUnit());
        price.setText(p == null ? "" : p.getCurrentPrice().amount().toPlainString().replace('.', ','));
        if (p != null && vat.getItems().stream().noneMatch(v -> v.compareTo(p.getVatRate()) == 0))
            vat.getItems().add(p.getVatRate());            // A-39: a rate outside the standard list is kept
        vat.setValue(p == null ? new BigDecimal("12") : vat.getItems().stream()
                .filter(v -> v.compareTo(p.getVatRate()) == 0).findFirst().orElse(null));
        allergens.forEach((id, box) -> box.setSelected(p != null && p.getAllergenList().stream().anyMatch(a -> a.id() == id)));
        byte[] stored = null;
        try {
            if (p != null) stored = catalog.loadPhoto(p.getId());
        } catch (Exception e) {
            Ui.error(e);
        }
        showPhoto(stored);
        history.setText(p == null ? "" : priceHistory(p));
        delete.setDisable(p == null);
    }

    private String priceHistory(Product p) {
        String changes = load(() -> catalog.findPriceHistory(p.getId())).stream().limit(5)
                .map(h -> Ui.date(h.changedAt()) + ": " + h.oldPrice().format() + " → " + h.newPrice().format())
                .collect(Collectors.joining("\n"));
        return changes.isEmpty() ? "Cena se zatím neměnila." : "Historie cen:\n" + changes;
    }

    private void showPhoto(byte[] bytes) {
        photoView.setImage(bytes == null ? null : new Image(new ByteArrayInputStream(bytes)));
        photoInfo.setText(bytes == null ? "Bez fotografie" : Math.round(bytes.length / 1024.0) + " kB");
    }

    private void choosePhoto() {
        Ui.prompter.openFile(stage).ifPresent((Path file) -> {
            try {
                if (Files.size(file) > Product.MAX_PHOTO_BYTES) {                  // A-39: same limit as the service
                    Ui.warn("Fotografie má nejvýše 100 kB.");
                    return;
                }
                newPhoto = Files.readAllBytes(file);
                showPhoto(newPhoto);
            } catch (IOException e) {
                Ui.error(e);
            }
        });
    }

    private void save() {
        boolean isNew = editing == null;
        List<Allergen> selected = allergenList.stream().filter(a -> allergens.get(a.id()).isSelected()).toList();
        try {
            BigDecimal amount = parsePrice(price.getText());
            Product saved = isNew
                    ? catalog.create(name.getText(), description.getText(), unit.getValue(), amount, vat.getValue(), selected, newPhoto)
                    : catalog.update(editing.getId(), name.getText(), description.getText(), unit.getValue(), amount,
                            vat.getValue(), selected, newPhoto);
            reload(saved.getId());
            fill(saved);                                   // A-36
            Ui.info(isNew ? "Produkt byl uložen." : "Změny byly uloženy.");
        } catch (ValidationException e) {
            if (!errors.show(e)) Ui.error(e);
        } catch (Exception e) {
            Ui.error(e);
        }
    }

    /** "1 234,50" -> 1234.50; null when not a number (the domain then reports the price field). */
    private static BigDecimal parsePrice(String text) {
        try {
            return new BigDecimal(text.replace(" ", "").replace(',', '.'));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private void delete() {
        if (editing == null || !Ui.confirm("Opravdu smazat produkt " + editing.getName() + "?")) return;
        try {
            catalog.delete(editing.getId());
            reload(null);
            fill(null);
            Ui.info("Produkt byl smazán.");
        } catch (Exception e) {
            Ui.error(e);
        }
    }
}
