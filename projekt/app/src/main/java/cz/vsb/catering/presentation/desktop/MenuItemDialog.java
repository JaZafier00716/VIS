package cz.vsb.catering.presentation.desktop;

import cz.vsb.catering.business.domain.MenuItem;
import cz.vsb.catering.business.domain.Money;
import cz.vsb.catering.business.domain.Product;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import javafx.event.ActionEvent;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;

/** UC08 step 3: picks a catalogue product (new item) or changes quantity, unit price and note. */
final class MenuItemDialog {
    /** A-54: the item note is printed on the customer's web page. */
    static final String NOTE_LABEL = "Poznámka (vidí zákazník)";

    record Values(Product product, BigDecimal quantity, Money unitPrice, String note) {}

    private MenuItemDialog() {}

    /** New item: quantity defaults to the guest count, price to the catalogue price. */
    static Optional<Values> add(List<Product> products, int guests) {
        return show("Přidat položku z katalogu", products, null, BigDecimal.valueOf(guests), null, null);
    }

    static Optional<Values> edit(MenuItem item) {
        return show("Upravit položku", null, item.getProduct(), item.getQuantity(), item.getUnitPrice(), item.getNote());
    }

    private static Optional<Values> show(String title, List<Product> products, Product fixed, BigDecimal quantity,
                                         Money price, String note) {
        Dialog<Values> dialog = new Dialog<>();
        dialog.setTitle(title);
        dialog.setHeaderText(title);
        Ui.style(dialog.getDialogPane());
        ComboBox<Product> product = new ComboBox<>();
        TextField qty = new TextField(quantity.stripTrailingZeros().toPlainString());
        TextField unitPrice = new TextField(price == null ? "" : price.amount().toPlainString());
        TextField noteField = new TextField(note == null ? "" : note);
        Label unit = new Label();
        Label error = Ui.label("", "error-text");
        if (fixed == null) {
            noteField.setDisable(true);                       // AddItemCommand has no note; set it via Upravit položku
            product.getItems().setAll(products);
            product.valueProperty().addListener((o, a, p) -> {
                if (p == null) return;
                unitPrice.setText(p.getCurrentPrice().amount().toPlainString());
                unit.setText(p.getUnitLabel());
            });
        } else {
            product.getItems().setAll(fixed);
            product.setValue(fixed);
            product.setDisable(true);
            unit.setText(fixed.getUnitLabel());
        }
        product.setPrefWidth(320);
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(8);
        grid.addRow(0, new Label("Produkt"), product);
        grid.addRow(1, new Label("Množství"), qty, unit);
        grid.addRow(2, new Label("Jedn. cena (Kč bez DPH)"), unitPrice);
        grid.addRow(3, new Label(NOTE_LABEL), noteField);
        grid.add(error, 0, 4, 3, 1);
        dialog.getDialogPane().setContent(grid);

        ButtonType ok = new ButtonType("OK", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(new ButtonType("Zrušit", ButtonBar.ButtonData.CANCEL_CLOSE), ok);
        dialog.getDialogPane().lookupButton(ok).getStyleClass().add("primary");
        Values[] result = new Values[1];
        dialog.getDialogPane().lookupButton(ok).addEventFilter(ActionEvent.ACTION, ev -> {
            try {
                if (product.getValue() == null) throw new IllegalArgumentException("Vyberte produkt.");
                result[0] = new Values(product.getValue(), number(qty.getText(), "Množství"),
                        Money.of(number(unitPrice.getText(), "Cena")), noteField.getText().isBlank() ? null : noteField.getText().trim());
            } catch (IllegalArgumentException e) {
                error.setText(e.getMessage());
                ev.consume();
            }
        });
        dialog.setResultConverter(b -> b == ok ? result[0] : null);
        return dialog.showAndWait();
    }

    /** Accepts the Czech decimal comma; the >0 / >=0 rules are checked by MenuRevision on save. */
    static BigDecimal number(String text, String field) {
        try {
            return new BigDecimal(text.trim().replace(" ", "").replace(',', '.'));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(field + " musí být číslo.");
        }
    }
}
