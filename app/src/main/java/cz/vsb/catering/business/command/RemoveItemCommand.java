package cz.vsb.catering.business.command;

import cz.vsb.catering.business.domain.MenuCategory;
import cz.vsb.catering.business.domain.MenuItem;
import cz.vsb.catering.business.domain.MenuRevision;

public class RemoveItemCommand implements MenuEditCommand {
    private final MenuRevision revision;
    private final MenuCategory category;
    private final MenuItem item;
    private int index;

    public RemoveItemCommand(MenuRevision revision, MenuCategory category, MenuItem item) {
        this.revision = revision;
        this.category = category;
        this.item = item;
    }

    @Override public void execute() { index = revision.removeItem(category, item); }

    @Override public void undo() { revision.insertItem(category, index, item); }

    @Override public String description() { return "Odebrat " + item.getProduct().getName(); }
}
