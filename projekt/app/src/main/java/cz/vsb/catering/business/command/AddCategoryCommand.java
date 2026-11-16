package cz.vsb.catering.business.command;

import cz.vsb.catering.business.domain.MenuCategory;
import cz.vsb.catering.business.domain.MenuRevision;

public class AddCategoryCommand implements MenuEditCommand {
    private final MenuRevision revision;
    private final String name;
    private MenuCategory created;

    public AddCategoryCommand(MenuRevision revision, String name) {
        this.revision = revision;
        this.name = name;
    }

    @Override public void execute() { created = revision.addCategory(name); }

    @Override public void undo() { revision.removeCategory(created); }

    @Override public String description() { return "Přidat kategorii " + name; }

    public MenuCategory getCreated() { return created; }
}
