package cz.vsb.catering.presentation.desktop;

import cz.vsb.catering.business.domain.Project;
import cz.vsb.catering.business.domain.ValidationException;
import cz.vsb.catering.business.service.ProjectService;
import java.util.Optional;
import java.util.function.Function;
import javafx.beans.property.SimpleStringProperty;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

/** UC04: finds a project by name, customer or place and by event date; returns the chosen project. */
class ProjectSearchDialog {
    private final ProjectService projects;
    private final Dialog<Project> dialog = new Dialog<>();
    final TextField text = new TextField();
    final DatePicker from = new DatePicker(), to = new DatePicker();
    final CheckBox archived = new CheckBox("včetně archivovaných");
    final Button search = new Button("Hledat");
    final TableView<Project> results = new TableView<>();
    final Label count = Ui.label("", "muted");
    private final Ui.FieldErrors errors = new Ui.FieldErrors();

    ProjectSearchDialog(ProjectService projects, String initialText) {
        this.projects = projects;
        dialog.setTitle("Vyhledat projekt");
        dialog.setHeaderText(null);
        Ui.style(dialog.getDialogPane());
        text.setPromptText("Název, zákazník nebo místo");
        text.setText(initialText == null ? "" : initialText);
        HBox.setHgrow(text, Priority.ALWAYS);
        from.setPromptText("Datum od");
        to.setPromptText("Datum do");
        from.setPrefWidth(130);
        to.setPrefWidth(130);
        search.getStyleClass().add("primary");
        search.setDefaultButton(true);
        search.setOnAction(e -> search());
        HBox criteria = new HBox(8, text, from, errors.add("to", to), search);
        criteria.setAlignment(javafx.geometry.Pos.TOP_LEFT);

        results.getColumns().add(col("Název", 200, Project::getName));
        results.getColumns().add(col("Zákazník", 180, p -> p.getCustomer().getName()
                + (p.getCustomer().getCompanyName() == null ? "" : " (" + p.getCustomer().getCompanyName() + ")")));
        results.getColumns().add(col("Datum akce", 90, p -> Ui.date(p.getEventDate())));
        results.getColumns().add(col("Místo", 180, Project::getEventPlace));
        results.getColumns().add(col("Stav", 100, p -> p.getStatus().label()));
        results.setPlaceholder(new Label("Žádný projekt neodpovídá zadání."));
        results.setPrefHeight(320);
        results.setRowFactory(t -> {
            TableRow<Project> row = new TableRow<>();
            row.setOnMouseClicked(e -> {
                if (e.getClickCount() == 2 && row.getItem() != null) {
                    dialog.setResult(row.getItem());
                    dialog.close();
                }
            });
            return row;
        });
        dialog.getDialogPane().setContent(new VBox(8, criteria, new HBox(archived, Ui.spacer(), count), results));
        dialog.getDialogPane().setPrefWidth(860);

        ButtonType open = new ButtonType("Otevřít projekt", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(new ButtonType("Zrušit", ButtonBar.ButtonData.CANCEL_CLOSE), open);
        Button openButton = (Button) dialog.getDialogPane().lookupButton(open);
        openButton.disableProperty().bind(results.getSelectionModel().selectedItemProperty().isNull());
        openButton.setDefaultButton(false);
        dialog.setResultConverter(b -> b == open ? results.getSelectionModel().getSelectedItem() : null);
        search();
    }

    Optional<Project> show() { return dialog.showAndWait(); }

    /** Runs the search with the current criteria; returns the number of hits (-1 = wrong criteria). */
    int search() {
        try {
            results.getItems().setAll(projects.searchProjects(text.getText(), from.getValue(), to.getValue(),
                    archived.isSelected()));
            errors.clear();
            count.setText("Nalezeno: " + Ui.plural(results.getItems().size(), "projekt", "projekty", "projektů"));
            return results.getItems().size();
        } catch (ValidationException e) {
            if (!errors.show(e)) Ui.error(e);
        } catch (Exception e) {
            Ui.error(e);
        }
        return -1;
    }

    Dialog<Project> dialog() { return dialog; }

    private static TableColumn<Project, String> col(String title, double width, Function<Project, String> value) {
        TableColumn<Project, String> c = new TableColumn<>(title);
        c.setCellValueFactory(d -> new SimpleStringProperty(value.apply(d.getValue())));
        c.setPrefWidth(width);
        return c;
    }
}
