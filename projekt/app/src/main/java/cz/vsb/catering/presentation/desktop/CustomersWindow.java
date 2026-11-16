package cz.vsb.catering.presentation.desktop;

import cz.vsb.catering.business.domain.Customer;
import cz.vsb.catering.business.domain.ValidationException;
import cz.vsb.catering.business.service.CustomerService;
import java.util.function.Function;
import javafx.animation.PauseTransition;
import javafx.beans.property.SimpleStringProperty;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.util.Duration;

/** UC05 Spravovat zákazníky: searchable list and a form for a new or the selected customer. */
class CustomersWindow {
    private final CustomerService customers;
    private final Stage stage = new Stage();
    final TableView<Customer> table = new TableView<>();
    final TextField search = new TextField();
    private final Label formTitle = Ui.label("", "h2");
    final TextField name = new TextField(), company = new TextField(), email = new TextField(),
            phone = new TextField(), city = new TextField();
    private final Ui.FieldErrors errors = new Ui.FieldErrors();
    final Button newCustomer = new Button("+ Nový zákazník"), save = new Button("Uložit"), delete = new Button("Smazat");
    private Customer editing;                                          // null = new customer

    CustomersWindow(CustomerService customers) {
        this.customers = customers;
        table.getColumns().add(col("Jméno", 180, Customer::getName));
        table.getColumns().add(col("Firma", 170, Customer::getCompanyName));
        table.getColumns().add(col("E-mail", 210, Customer::getEmail));
        table.getColumns().add(col("Telefon", 130, Customer::getPhone));
        table.getColumns().add(col("Město", 110, Customer::getCity));
        table.getSelectionModel().selectedItemProperty().addListener((o, a, c) -> { if (c != null) fill(c); });
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.setPlaceholder(new Label("Žádný zákazník neodpovídá hledání."));
        VBox.setVgrow(table, Priority.ALWAYS);
        search.setPromptText("Hledat podle jména, firmy, e-mailu, telefonu nebo města");
        PauseTransition debounce = new PauseTransition(Duration.millis(300));      // A-40
        debounce.setOnFinished(e -> reload(null));
        search.textProperty().addListener((o, a, t) -> debounce.playFromStart());
        newCustomer.setOnAction(e -> { table.getSelectionModel().clearSelection(); fill(null); });
        VBox left = new VBox(10, new HBox(Ui.label("Zákazníci", "h1"), Ui.spacer(), newCustomer), search, table);
        left.getStyleClass().add("content");
        HBox.setHgrow(left, Priority.ALWAYS);

        save.getStyleClass().add("primary");
        save.setOnAction(e -> save());
        delete.getStyleClass().add("danger");
        delete.setGraphic(Ui.icon(Ui.TRASH));
        delete.setOnAction(e -> delete());
        VBox form = new VBox(6, formTitle,
                new Label("Jméno a příjmení *"), errors.add("name", name),
                new Label("Firma"), errors.add("companyName", company),
                new Label("E-mail *"), errors.add("email", email),
                new Label("Telefon"), errors.add("phone", phone),
                new Label("Město"), errors.add("city", city),
                new HBox(8, delete, Ui.spacer(), save));
        form.getStyleClass().add("card");
        form.setPrefWidth(320);
        VBox right = new VBox(form);
        right.getStyleClass().add("content");

        stage.setTitle("Catering IS · Zákazníci");
        stage.setScene(Ui.scene(new BorderPane(left, null, right, null, null), 1200, 640));
        reload(null);
        fill(null);
    }

    Stage stage() { return stage; }

    private static TableColumn<Customer, String> col(String title, double width, Function<Customer, String> value) {
        TableColumn<Customer, String> c = new TableColumn<>(title);
        c.setCellValueFactory(d -> new SimpleStringProperty(value.apply(d.getValue())));
        c.setPrefWidth(width);
        return c;
    }

    private void reload(Long selectId) {
        try {
            table.getItems().setAll(customers.search(search.getText()));
        } catch (Exception e) {
            Ui.error(e);
        }
        table.getItems().stream().filter(c -> c.getId().equals(selectId)).findFirst()
                .ifPresent(table.getSelectionModel()::select);
    }

    private void fill(Customer c) {
        editing = c;
        errors.clear();
        formTitle.setText(c == null ? "Nový zákazník" : "Upravit zákazníka");
        name.setText(c == null ? "" : c.getName());
        company.setText(c == null ? "" : orEmpty(c.getCompanyName()));
        email.setText(c == null ? "" : c.getEmail());
        phone.setText(c == null ? "" : orEmpty(c.getPhone()));
        city.setText(c == null ? "" : orEmpty(c.getCity()));
        delete.setDisable(c == null);
    }

    private static String orEmpty(String s) { return s == null ? "" : s; }

    private void save() {
        boolean isNew = editing == null;
        try {
            Customer saved = isNew
                    ? customers.create(name.getText(), company.getText(), email.getText(), phone.getText(), city.getText())
                    : customers.update(editing.getId(), name.getText(), company.getText(), email.getText(), phone.getText(), city.getText());
            reload(saved.getId());
            fill(saved);                                   // A-36: the form leaves "new" mode even when a filter hides the row
            Ui.info(isNew ? "Zákazník byl uložen." : "Změny byly uloženy.");
        } catch (ValidationException e) {
            if (!errors.show(e)) Ui.error(e);
        } catch (Exception e) {
            Ui.error(e);
        }
    }

    private void delete() {
        if (editing == null || !Ui.confirm("Opravdu smazat zákazníka " + editing.displayName() + "?")) return;
        try {
            customers.delete(editing.getId());
            reload(null);
            fill(null);
            Ui.info("Zákazník byl smazán.");
        } catch (Exception e) {
            Ui.error(e);
        }
    }
}
