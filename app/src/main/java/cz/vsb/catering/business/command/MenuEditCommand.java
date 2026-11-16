package cz.vsb.catering.business.command;

/** Command (GoF): one edit of a menu revision as an object that can be executed and undone. */
public interface MenuEditCommand {
    void execute();

    void undo();

    /** Czech description for the history / undo button, e.g. "Přidat kategorii Předkrmy". */
    String description();
}
