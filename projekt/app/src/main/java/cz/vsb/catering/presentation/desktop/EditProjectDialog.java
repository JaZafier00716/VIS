package cz.vsb.catering.presentation.desktop;

import cz.vsb.catering.business.domain.Project;
import cz.vsb.catering.business.domain.User;
import cz.vsb.catering.business.domain.ValidationException;
import cz.vsb.catering.business.service.ProjectService;
import java.util.Optional;
import javafx.event.ActionEvent;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;

/** UC03: edits the event data of a project (same form and field errors as NewProjectDialog) and
 *  archives it; an archived project can be restored ("Obnovit projekt"). Customer and responsible worker are shown but cannot change. An archived project
 *  is shown read-only. The dialog returns the changed project, or empty when nothing was saved. */
class EditProjectDialog {
    private final ProjectService projects;
    private final User user;
    private final Dialog<Project> dialog = new Dialog<>();
    final TextField name = new TextField();
    final DatePicker date = new DatePicker();
    final TextField guests = new TextField();
    final TextField place = new TextField();
    final TextArea note = new TextArea();
    final Button archive;
    final Button reactivate;
    private final Ui.FieldErrors errors = new Ui.FieldErrors();
    private Project project;
    private boolean changed;

    EditProjectDialog(ProjectService projects, Project project, User user) {
        this.projects = projects;
        this.user = user;
        this.project = project;
        dialog.setTitle("Upravit projekt");
        dialog.setHeaderText("Upravit projekt\nZákazník: " + project.getCustomer().getName()
                + (project.getCustomer().getCompanyName() == null ? "" : ", " + project.getCustomer().getCompanyName())
                + " · Stav: " + project.getStatus().label());
        Ui.style(dialog.getDialogPane());
        name.setText(project.getName());
        date.setValue(project.getEventDate());
        guests.setText(String.valueOf(project.getGuestCount()));
        place.setText(project.getEventPlace());
        note.setText(project.getNote() == null ? "" : project.getNote());
        note.setPrefRowCount(3);
        note.setWrapText(true);

        GridPane grid = new GridPane();
        grid.setHgap(12);
        grid.setVgap(4);
        grid.add(new Label("Název projektu *"), 0, 0, 2, 1);
        grid.add(errors.add("name", name), 0, 1, 2, 1);
        grid.add(new Label("Datum akce *"), 0, 2);
        grid.add(new Label("Počet hostů *"), 1, 2);
        grid.add(errors.add("eventDate", date), 0, 3);
        grid.add(errors.add("guestCount", guests), 1, 3);
        grid.add(new Label("Místo konání *"), 0, 4, 2, 1);
        grid.add(errors.add("eventPlace", place), 0, 5, 2, 1);
        grid.add(new Label("Poznámka"), 0, 6, 2, 1);
        grid.add(errors.add("note", note), 0, 7, 2, 1);
        grid.add(Ui.label("* povinné údaje · zákazníka projektu nelze změnit", "muted"), 0, 8, 2, 1);
        dialog.getDialogPane().setContent(grid);
        dialog.getDialogPane().setPrefWidth(480);

        ButtonType archiveType = new ButtonType("Archivovat", ButtonBar.ButtonData.LEFT);
        ButtonType reactivateType = new ButtonType("Obnovit projekt", ButtonBar.ButtonData.LEFT);
        ButtonType save = new ButtonType("Uložit změny", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(archiveType, reactivateType, new ButtonType("Zrušit", ButtonBar.ButtonData.CANCEL_CLOSE), save);
        archive = (Button) dialog.getDialogPane().lookupButton(archiveType);
        archive.getStyleClass().add("danger");
        archive.addEventFilter(ActionEvent.ACTION, ev -> { if (!archive()) ev.consume(); });
        reactivate = (Button) dialog.getDialogPane().lookupButton(reactivateType);
        reactivate.setVisible(project.isArchived());                         // only an archived project can be restored
        reactivate.setManaged(project.isArchived());
        reactivate.addEventFilter(ActionEvent.ACTION, ev -> { if (!reactivate()) ev.consume(); });
        archive.setVisible(!project.isArchived());
        archive.setManaged(!project.isArchived());
        Button saveButton = (Button) dialog.getDialogPane().lookupButton(save);
        saveButton.getStyleClass().add("primary");
        saveButton.addEventFilter(ActionEvent.ACTION, ev -> { if (submit() == null) ev.consume(); });
        if (project.isArchived()) {                                          // read-only, only Zrušit stays usable
            for (var field : new javafx.scene.control.Control[]{name, date, guests, place, note}) field.setDisable(true);
            archive.setDisable(true);
            saveButton.setDisable(true);
        }
        dialog.setResultConverter(b -> changed ? this.project : null);
    }

    Optional<Project> show() { return dialog.showAndWait(); }

    /** Saves the form; returns the updated project, or null when a field is wrong (it is highlighted). */
    Project submit() {
        try {
            project = projects.updateProject(project.getId(), name.getText(), date.getValue(), place.getText(),
                    parseInt(guests.getText()), note.getText());
            errors.clear();
            changed = true;
            return project;
        } catch (ValidationException e) {
            if (!errors.show(e)) Ui.error(e);
        } catch (Exception e) {
            Ui.error(e);
        }
        return null;
    }

    /** After confirmation the project is archived; returns false when cancelled or refused. */
    boolean archive() {
        if (edited() && !Ui.confirm("Neuložené změny se ztratí. Archivovat?")) return false;    // A-39
        if (!Ui.confirm("Archivovat projekt „" + project.getName() + "“? Archivovaný projekt už nelze upravovat.")) return false;
        try {
            project = projects.archiveProject(project.getId());
            changed = true;
            return true;
        } catch (Exception e) {
            Ui.error(e);
            return false;
        }
    }

    /** UC03: after confirmation the archived project is IN_PROGRESS again; returns false when cancelled or refused. */
    boolean reactivate() {
        if (!Ui.confirm("Obnovit projekt „" + project.getName() + "“? Bude opět možné jej upravovat.")) return false;
        try {
            project = projects.reactivateProject(project.getId(), user);
            changed = true;
            return true;
        } catch (Exception e) {
            Ui.error(e);
            return false;
        }
    }

    private boolean edited() {
        return !name.getText().equals(project.getName()) || !java.util.Objects.equals(date.getValue(), project.getEventDate())
                || !guests.getText().trim().equals(String.valueOf(project.getGuestCount()))
                || !place.getText().equals(project.getEventPlace())
                || !note.getText().equals(project.getNote() == null ? "" : project.getNote());
    }

    Dialog<Project> dialog() { return dialog; }

    private static Integer parseInt(String text) {
        try {
            return Integer.valueOf(text.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
