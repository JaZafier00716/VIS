package cz.vsb.catering.presentation.desktop;

import cz.vsb.catering.business.domain.Customer;
import cz.vsb.catering.business.domain.Project;
import cz.vsb.catering.business.domain.User;
import cz.vsb.catering.business.domain.ValidationException;
import cz.vsb.catering.business.service.ProjectService;
import java.util.Optional;
import javafx.event.ActionEvent;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;

/** UC02 steps 1-4 (and 4.1-4.3): form of a new project with field errors. */
class NewProjectDialog {
    private final ProjectService projects;
    private final User worker;
    private final Dialog<Project> dialog = new Dialog<>();
    final TextField name = new TextField();
    final ComboBox<Customer> customer = new ComboBox<>();
    final DatePicker date = new DatePicker();
    final TextField guests = new TextField();
    final TextField place = new TextField();
    private final Ui.FieldErrors errors = new Ui.FieldErrors();
    private Project created;

    NewProjectDialog(ProjectService projects, User worker) {
        this.projects = projects;
        this.worker = worker;
        dialog.setTitle("Nový projekt");
        dialog.setHeaderText("Nový projekt\nProjekt sdružuje dokumenty a menu jedné zakázky. Kořenová složka se vytvoří automaticky.");
        Ui.style(dialog.getDialogPane());
        customer.getItems().setAll(projects.findAllCustomers());           // UC02 step 2
        customer.setMaxWidth(Double.MAX_VALUE);

        GridPane grid = new GridPane();
        grid.setHgap(12);
        grid.setVgap(4);
        grid.add(new Label("Název projektu *"), 0, 0, 2, 1);
        grid.add(errors.add("name", name), 0, 1, 2, 1);
        grid.add(new Label("Zákazník *"), 0, 2, 2, 1);
        grid.add(errors.add("customer", customer), 0, 3, 2, 1);
        grid.add(new Label("Datum akce *"), 0, 4);
        grid.add(new Label("Počet hostů *"), 1, 4);
        grid.add(errors.add("eventDate", date), 0, 5);
        grid.add(errors.add("guestCount", guests), 1, 5);
        grid.add(new Label("Místo konání *"), 0, 6, 2, 1);
        grid.add(errors.add("eventPlace", place), 0, 7, 2, 1);
        grid.add(Ui.label("* povinné údaje", "muted"), 0, 8, 2, 1);
        dialog.getDialogPane().setContent(grid);
        dialog.getDialogPane().setPrefWidth(480);

        ButtonType create = new ButtonType("Vytvořit projekt", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(new ButtonType("Zrušit", ButtonBar.ButtonData.CANCEL_CLOSE), create);
        dialog.getDialogPane().lookupButton(create).getStyleClass().add("primary");
        dialog.getDialogPane().lookupButton(create).addEventFilter(ActionEvent.ACTION, ev -> {
            if (submit() == null) ev.consume();                            // 4.3: the worker corrects the data
        });
        dialog.setResultConverter(b -> b == create ? created : null);
    }

    Optional<Project> show() { return dialog.showAndWait(); }

    /** UC02 step 4: creates the project, or highlights the wrong fields and returns null. */
    Project submit() {
        try {
            Customer c = customer.getValue();
            created = projects.createProject(name.getText(), c == null ? null : c.getId(), date.getValue(),
                    place.getText(), parseInt(guests.getText()), worker);
            errors.clear();
        } catch (ValidationException e) {
            if (!errors.show(e)) Ui.error(e);
            created = null;
        } catch (Exception e) {
            Ui.error(e);
            created = null;
        }
        return created;
    }

    /** Not a number -> null, the business rule then reports "Počet hostů musí být větší než 0". */
    private static Integer parseInt(String text) {
        try {
            return Integer.valueOf(text.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
