package cz.vsb.catering.presentation.desktop;

import cz.vsb.catering.business.domain.Folder;
import cz.vsb.catering.business.domain.MenuItem;
import cz.vsb.catering.business.domain.Product;
import cz.vsb.catering.business.domain.ProjectNode;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import javafx.scene.control.Alert;
import javafx.stage.Window;

/** Test double of the dialog seam: answers come from queues, messages are recorded. */
class ScriptedPrompter implements Prompter {
    final Deque<String> texts = new ArrayDeque<>();
    final List<String> messages = new ArrayList<>();
    final List<String> renames = new ArrayList<>();
    boolean confirmAnswer = true;
    Path file, directory;
    Function<List<Product>, MenuItemDialog.Values> newItem;
    Function<MenuItem, MenuItemDialog.Values> changedItem;
    ShareDialog shown;
    final List<javafx.scene.control.Dialog<?>> dialogs = new ArrayList<>();
    Object dialogResult;

    @Override public Optional<String> text(String title, String label, String initial) {
        return Optional.ofNullable(texts.poll());
    }

    @Override public boolean confirm(String message) { return confirmAnswer; }

    @Override public void message(Alert.AlertType type, String title, String message) { messages.add(message); }

    /** Accepts the suggested free name (Přejmenovat). */
    @Override public Optional<String> rename(Folder folder, String name, String suggested) {
        renames.add(suggested);
        return Optional.of(suggested);
    }

    @Override public boolean confirmDelete(List<ProjectNode> nodes) { return confirmAnswer; }

    @Override public Optional<Path> openFile(Window owner) { return Optional.ofNullable(file); }

    @Override public Optional<Path> chooseDirectory(Window owner) { return Optional.ofNullable(directory); }

    @Override public Optional<MenuItemDialog.Values> addItem(List<Product> products, int guests) {
        return Optional.ofNullable(newItem.apply(products));
    }

    @Override public Optional<MenuItemDialog.Values> editItem(MenuItem item) {
        return Optional.ofNullable(changedItem.apply(item));
    }

    @Override public void show(ShareDialog dialog) { shown = dialog; }

    /** Records the dialog and answers with dialogResult (null = cancelled). */
    @SuppressWarnings("unchecked")
    @Override public <T> Optional<T> dialog(javafx.scene.control.Dialog<T> dialog) {
        dialogs.add(dialog);
        return Optional.ofNullable((T) dialogResult);
    }

    String lastMessage() { return messages.isEmpty() ? "" : messages.get(messages.size() - 1); }
}
