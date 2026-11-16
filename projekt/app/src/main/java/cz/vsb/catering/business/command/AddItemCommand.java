package cz.vsb.catering.business.command;

import cz.vsb.catering.business.domain.MenuCategory;
import cz.vsb.catering.business.domain.MenuItem;
import cz.vsb.catering.business.domain.MenuRevision;
import cz.vsb.catering.business.domain.Money;
import cz.vsb.catering.business.domain.Product;
import java.math.BigDecimal;

/** Adds a catalogue product; quantity defaults to the guest count, price to the catalogue price. */
public class AddItemCommand implements MenuEditCommand {
    private final MenuRevision revision;
    private final MenuCategory category;
    private final Product product;
    private final BigDecimal quantity;
    private final Money unitPrice;
    private MenuItem created;

    public AddItemCommand(MenuRevision revision, MenuCategory category, Product product,
                          BigDecimal quantity, Money unitPrice) {
        this.revision = revision;
        this.category = category;
        this.product = product;
        this.quantity = quantity;
        this.unitPrice = unitPrice;
    }

    @Override public void execute() { created = revision.addItem(category, product, quantity, unitPrice, null); }

    @Override public void undo() { revision.removeItem(category, created); }

    @Override public String description() { return "Přidat " + product.getName() + " do " + category.getName(); }

    public MenuItem getCreated() { return created; }
}
