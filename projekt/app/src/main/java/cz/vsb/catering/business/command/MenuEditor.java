package cz.vsb.catering.business.command;

import cz.vsb.catering.business.domain.MenuRevision;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;

/** Invoker of the Command pattern: runs edit commands on one revision, keeps the undo stack
 *  and a log of executed commands. */
public class MenuEditor {
    private final MenuRevision revision;
    private final Deque<MenuEditCommand> undoStack = new ArrayDeque<>();
    private final List<String> log = new ArrayList<>();

    public MenuEditor(MenuRevision revision) { this.revision = revision; }

    /** Executes the command; a failed command (exception) is neither logged nor undoable. */
    public void execute(MenuEditCommand command) {
        command.execute();
        undoStack.push(command);
        log.add(command.description());
    }

    public boolean canUndo() { return !undoStack.isEmpty(); }

    public String undo() {
        MenuEditCommand command = undoStack.pop();
        command.undo();
        log.add("Zpět: " + command.description());
        return command.description();
    }

    public MenuRevision getRevision() { return revision; }

    public List<String> getLog() { return Collections.unmodifiableList(log); }
}
