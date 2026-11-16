package cz.vsb.catering.business.domain;

import cz.vsb.catering.business.pricing.PriceStrategy;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/** Category of one revision (e.g. Předkrmy); id is menu_categories.category_id. */
public class MenuCategory extends DomainObject {
    private String name;
    private final List<MenuItem> items = new ArrayList<>();

    public MenuCategory(String name) { this.name = name; }

    public Money subtotal(PriceStrategy strategy) {
        return items.stream().map(i -> i.price(strategy)).reduce(Money.ZERO, Money::plus);
    }

    public Optional<MenuItem> findItem(long productId) {
        return items.stream().filter(i -> i.getProduct().getId() == productId).findFirst();
    }

    public String getName() { return name; }
    public List<MenuItem> getItems() { return Collections.unmodifiableList(items); }

    /** Used by the mapper when loading. */
    public void loadItem(MenuItem item) { items.add(item); }

    void rename(String name) { this.name = name; }
    void addItem(MenuItem item) { items.add(item); }
    void insertItem(int index, MenuItem item) { items.add(index, item); }
    void removeItem(MenuItem item) { items.remove(item); }

    MenuCategory copy() {
        MenuCategory copy = new MenuCategory(name);
        items.forEach(i -> copy.items.add(i.copy()));
        return copy;
    }
}
