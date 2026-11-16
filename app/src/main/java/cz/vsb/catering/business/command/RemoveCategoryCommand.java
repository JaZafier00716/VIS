package cz.vsb.catering.business.command;

import cz.vsb.catering.business.domain.MenuCategory;
import cz.vsb.catering.business.domain.MenuRevision;

public class RemoveCategoryCommand implements MenuEditCommand {
    private final MenuRevision revision;
    private final MenuCategory category;
    private int index;

    public RemoveCategoryCommand(MenuRevision revision, MenuCategory category) {
        this.revision = revision;
        this.category = category;
    }

    @Override public void execute() { index = revision.removeCategory(category); }

    @Override public void undo() { revision.insertCategory(index, category); }

    @Override public String description() { return "Odebrat kategorii " + category.getName(); }
}
