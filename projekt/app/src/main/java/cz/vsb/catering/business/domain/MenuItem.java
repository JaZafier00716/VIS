package cz.vsb.catering.business.domain;

import cz.vsb.catering.business.pricing.PriceStrategy;
import java.math.BigDecimal;

/** Product in a menu category with quantity and unit price (without VAT). */
public class MenuItem {
    private final Product product;
    private BigDecimal quantity;
    private Money unitPrice;
    private String note;

    public MenuItem(Product product, BigDecimal quantity, Money unitPrice, String note) {
        this.product = product;
        this.quantity = quantity;
        this.unitPrice = unitPrice;
        this.note = note;
    }

    /** Price of the line computed by the chosen strategy (with or without VAT). */
    public Money price(PriceStrategy strategy) { return strategy.lineTotal(this); }

    public MenuItem copy() { return new MenuItem(product, quantity, unitPrice, note); }

    public Product getProduct() { return product; }
    public BigDecimal getQuantity() { return quantity; }
    public Money getUnitPrice() { return unitPrice; }
    public String getNote() { return note; }

    void change(BigDecimal quantity, Money unitPrice, String note) {
        this.quantity = quantity;
        this.unitPrice = unitPrice;
        this.note = note;
    }
}
