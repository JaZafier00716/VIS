package cz.vsb.catering.business.domain;

import cz.vsb.catering.business.pricing.PriceStrategy;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** UC10: differences between two revisions of one menu, one row per (category name, product).
 *  A product moved to another category is REMOVED from the old one and ADDED to the new one. */
public class RevisionComparison {
    public enum Status {
        ADDED("Přidáno"), REMOVED("Odebráno"), CHANGED("Změněno"), UNCHANGED("Beze změny");

        private final String label;

        Status(String label) { this.label = label; }

        public String label() { return label; }
    }

    /** left = item in the older (first) revision, right = in the newer one; null when missing. */
    public record Row(Status status, String category, Product product, MenuItem left, MenuItem right,
                      List<String> changes) {}

    private final MenuRevision older;
    private final MenuRevision newer;
    private final List<Row> rows = new ArrayList<>();

    public RevisionComparison(MenuRevision older, MenuRevision newer) {
        this.older = older;
        this.newer = newer;
        Set<String> categories = new LinkedHashSet<>();
        older.getCategories().forEach(c -> categories.add(c.getName()));
        newer.getCategories().forEach(c -> categories.add(c.getName()));
        for (String category : categories) {
            Map<Long, MenuItem> left = items(older, category), right = items(newer, category);
            Set<Long> products = new LinkedHashSet<>(left.keySet());
            products.addAll(right.keySet());
            for (Long productId : products) rows.add(row(category, left.get(productId), right.get(productId)));
        }
    }

    private static Row row(String category, MenuItem a, MenuItem b) {
        if (a == null) return new Row(Status.ADDED, category, b.getProduct(), null, b, List.of());
        if (b == null) return new Row(Status.REMOVED, category, a.getProduct(), a, null, List.of());
        List<String> changes = new ArrayList<>();
        if (a.getQuantity().compareTo(b.getQuantity()) != 0) changes.add("množství");
        if (!a.getUnitPrice().equals(b.getUnitPrice())) changes.add("jedn. cena");
        if (!Objects.equals(a.getNote(), b.getNote())) changes.add("poznámka");
        return new Row(changes.isEmpty() ? Status.UNCHANGED : Status.CHANGED, category, b.getProduct(), a, b,
                List.copyOf(changes));
    }

    /** Items of the category with this name keyed by product id, in menu order. */
    private static Map<Long, MenuItem> items(MenuRevision revision, String category) {
        Map<Long, MenuItem> result = new LinkedHashMap<>();
        revision.getCategories().stream().filter(c -> c.getName().equals(category))
                .forEach(c -> c.getItems().forEach(i -> result.put(i.getProduct().getId(), i)));
        return result;
    }

    public List<Row> getRows() { return Collections.unmodifiableList(rows); }

    /** Rows other than UNCHANGED. */
    public List<Row> changes() { return rows.stream().filter(r -> r.status() != Status.UNCHANGED).toList(); }

    public MenuRevision getOlder() { return older; }
    public MenuRevision getNewer() { return newer; }

    public Money olderTotal(PriceStrategy strategy) { return older.totalPrice(strategy); }

    public Money newerTotal(PriceStrategy strategy) { return newer.totalPrice(strategy); }

    /** Newer total minus older total (positive = the menu got more expensive). */
    public Money difference(PriceStrategy strategy) { return newerTotal(strategy).minus(olderTotal(strategy)); }
}
