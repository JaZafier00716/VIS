package cz.vsb.catering.presentation.desktop;

import cz.vsb.catering.business.domain.Folder;
import cz.vsb.catering.business.domain.MenuItem;
import cz.vsb.catering.business.domain.Product;
import cz.vsb.catering.business.domain.ProjectNode;
import java.io.File;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.TextInputDialog;
import javafx.scene.layout.Region;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.Window;

/** Every modal dialog of the desktop UI goes through this seam (Ui.prompter), so the UI tests can
 *  answer with scripted values instead of blocking on showAndWait. */
interface Prompter {
    Optional<String> text(String title, String label, String initial);

    boolean confirm(String message);

    void message(Alert.AlertType type, String title, String message);

    /** UC02 8.1: name clash, the suggested free name is pre-filled; empty = Zrušit. */
    Optional<String> rename(Folder folder, String name, String suggested);

    boolean confirmDelete(List<ProjectNode> nodes);

    Optional<Path> openFile(Window owner);

    Optional<Path> chooseDirectory(Window owner);

    Optional<MenuItemDialog.Values> addItem(List<Product> products, int guests);

    Optional<MenuItemDialog.Values> editItem(MenuItem item);

    void show(ShareDialog dialog);

    /** Any other modal dialog with a result (EditProjectDialog, ProjectSearchDialog). */
    default <T> Optional<T> dialog(Dialog<T> dialog) { return dialog.showAndWait(); }

    /** The real dialogs. JavaFX has no Czech texts for its default buttons, so they are named here. */
    class Dialogs implements Prompter {
        static final ButtonType OK = new ButtonType("OK", ButtonBar.ButtonData.OK_DONE);
        static final ButtonType CANCEL = new ButtonType("Zrušit", ButtonBar.ButtonData.CANCEL_CLOSE);

        @Override public Optional<String> text(String title, String label, String initial) {
            TextInputDialog d = new TextInputDialog(initial);
            d.setTitle(title);
            d.setHeaderText(null);
            d.setContentText(label);
            d.getDialogPane().getButtonTypes().setAll(OK, CANCEL);
            Ui.style(d.getDialogPane());
            return d.showAndWait();
        }

        @Override public boolean confirm(String message) {
            Alert a = new Alert(Alert.AlertType.CONFIRMATION, message, OK, CANCEL);
            a.setTitle("Potvrzení");
            a.setHeaderText(null);
            Ui.style(a.getDialogPane());
            return a.showAndWait().orElse(CANCEL) == OK;
        }

        @Override public void message(Alert.AlertType type, String title, String message) {
            Alert a = new Alert(type, message, OK);
            a.setTitle(title);
            a.setHeaderText(null);
            a.getDialogPane().setMinHeight(Region.USE_PREF_SIZE);
            Ui.style(a.getDialogPane());
            a.showAndWait();
        }

        @Override public Optional<String> rename(Folder folder, String name, String suggested) {
            return UploadConflictDialog.ask(folder, name, suggested);
        }

        @Override public boolean confirmDelete(List<ProjectNode> nodes) { return DeleteDialog.confirm(nodes); }

        @Override public Optional<Path> openFile(Window owner) {
            FileChooser chooser = new FileChooser();
            chooser.setTitle("Vybrat soubor");
            return Optional.ofNullable(chooser.showOpenDialog(owner)).map(File::toPath);
        }

        @Override public Optional<Path> chooseDirectory(Window owner) {
            DirectoryChooser chooser = new DirectoryChooser();
            chooser.setTitle("Vybrat složku");
            return Optional.ofNullable(chooser.showDialog(owner)).map(File::toPath);
        }

        @Override public Optional<MenuItemDialog.Values> addItem(List<Product> products, int guests) {
            return MenuItemDialog.add(products, guests);
        }

        @Override public Optional<MenuItemDialog.Values> editItem(MenuItem item) { return MenuItemDialog.edit(item); }

        @Override public void show(ShareDialog dialog) { dialog.show(); }
    }
}
