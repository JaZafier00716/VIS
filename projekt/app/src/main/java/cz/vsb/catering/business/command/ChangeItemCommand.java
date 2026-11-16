package cz.vsb.catering.business.command;

import cz.vsb.catering.business.domain.MenuItem;
import cz.vsb.catering.business.domain.MenuRevision;
import cz.vsb.catering.business.domain.Money;
import java.math.BigDecimal;

public class ChangeItemCommand implements MenuEditCommand {
    private final MenuRevision revision;
    private final MenuItem item;
    private final BigDecimal newQuantity;
    private final Money newPrice;
    private final String newNote;
    private BigDecimal oldQuantity;
    private Money oldPrice;
    private String oldNote;

    public ChangeItemCommand(MenuRevision revision, MenuItem item, BigDecimal newQuantity, Money newPrice, String newNote) {
        this.revision = revision;
        this.item = item;
        this.newQuantity = newQuantity;
        this.newPrice = newPrice;
        this.newNote = newNote;
    }

    @Override public void execute() {
        oldQuantity = item.getQuantity();
        oldPrice = item.getUnitPrice();
        oldNote = item.getNote();
        revision.changeItem(item, newQuantity, newPrice, newNote);
    }

    @Override public void undo() { revision.changeItem(item, oldQuantity, oldPrice, oldNote); }

    @Override public String description() { return "Změnit " + item.getProduct().getName(); }
}
