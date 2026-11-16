package cz.vsb.catering.presentation.desktop;

import cz.vsb.catering.business.domain.DuplicateNameException;
import cz.vsb.catering.business.domain.Folder;
import java.util.Optional;
import java.util.function.Function;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;

/** UC02 8.1-8.5: name clash when creating a folder/menu or uploading a document. */
final class UploadConflictDialog {
    private UploadConflictDialog() {}

    /** Calls create with the name; on a clash offers the suggested free name until the name is free
     *  or the user cancels (then returns empty and nothing is created). */
    static <T> Optional<T> createWithRename(Folder folder, String name, Function<String, T> create) {
        String current = name;
        while (true) {
            try {
                return Optional.of(create.apply(current));
            } catch (DuplicateNameException e) {
                Optional<String> renamed = Ui.prompter.rename(folder, current, e.getSuggestedName());
                if (renamed.isEmpty()) return Optional.empty();
                current = renamed.get();
            }
        }
    }

    static Optional<String> ask(Folder folder, String name, String suggested) {
        Dialog<String> dialog = new Dialog<>();
        dialog.setTitle("Název již existuje");
        dialog.setHeaderText("Název již existuje");
        Ui.style(dialog.getDialogPane());
        TextField field = new TextField(suggested);
        Label text = new Label("Ve složce „" + folder.getName() + "“ už je položka „" + name + "“. Zvolte jiný název.");
        text.setWrapText(true);
        dialog.getDialogPane().setContent(new VBox(6, text, Ui.label("Nový název", "h2"), field,
                Ui.label("Navržený název je ve složce volný. Můžete jej upravit.", "muted")));
        ButtonType rename = new ButtonType("Přejmenovat", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(new ButtonType("Zrušit", ButtonBar.ButtonData.CANCEL_CLOSE), rename);
        dialog.getDialogPane().lookupButton(rename).getStyleClass().add("primary");
        dialog.setResultConverter(b -> b == rename ? field.getText() : null);
        return dialog.showAndWait();
    }
}
