package cz.vsb.catering.presentation.desktop;

import cz.vsb.catering.business.domain.Document;
import cz.vsb.catering.business.domain.ProjectNode;
import java.util.List;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

/** UC19: confirmation listing the selected items before they are deleted. */
final class DeleteDialog {
    private DeleteDialog() {}

    static boolean confirm(List<ProjectNode> nodes) {
        Dialog<Boolean> dialog = new Dialog<>();
        dialog.setTitle("Smazat");
        dialog.setHeaderText("Opravdu smazat " + Ui.plural(nodes.size(), "položku", "položky", "položek") + "?");
        Ui.style(dialog.getDialogPane());
        VBox list = new VBox(4);
        list.getStyleClass().add("card");
        for (ProjectNode n : nodes) {
            String size = n instanceof Document d ? d.formattedSize() : n.type().label();
            HBox row = new HBox(8, Ui.icon(n.type()), new Label(n.getName()), Ui.spacer(), Ui.label(size, "muted"));
            list.getChildren().add(row);
        }
        dialog.getDialogPane().setContent(new VBox(8, Ui.label("Tuto akci nelze vrátit.", "muted"), list));
        dialog.getDialogPane().setPrefWidth(420);
        ButtonType delete = new ButtonType("Smazat", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(new ButtonType("Zrušit", ButtonBar.ButtonData.CANCEL_CLOSE), delete);
        var deleteButton = (javafx.scene.control.Button) dialog.getDialogPane().lookupButton(delete);
        deleteButton.getStyleClass().add("danger");
        deleteButton.setGraphic(Ui.icon(Ui.TRASH));
        dialog.setResultConverter(b -> b == delete);
        return dialog.showAndWait().orElse(false);
    }
}
