package cz.vsb.catering.business.fake;

import cz.vsb.catering.business.domain.Allergen;
import cz.vsb.catering.business.domain.PriceChange;
import cz.vsb.catering.business.domain.Product;
import cz.vsb.catering.business.port.ProductMapper;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** In-memory catalogue; stores copies and hands out copies, photos kept apart (like the BLOB column). */
public class FakeProductMapper implements ProductMapper {
    public static final List<Allergen> ALLERGENS = List.of(new Allergen(1, "Lepek"), new Allergen(3, "Vejce"),
            new Allergen(7, "Mléko"));
    public final List<Product> products = new ArrayList<>();
    public final Map<Long, byte[]> photos = new HashMap<>();
    public final Set<Long> usedInMenu = new HashSet<>();
    private long nextId = 100;

    public FakeProductMapper(Product... initial) {
        for (Product p : initial) products.add(copy(p));
    }

    @Override public List<Product> findAll() { return products.stream().map(FakeProductMapper::copy).toList(); }

    @Override public List<Allergen> findAllergens() { return ALLERGENS; }

    @Override public void insert(Product product) {
        product.setId(nextId++);
        products.add(copy(product));
        if (product.getPhoto() != null) photos.put(product.getId(), product.getPhoto());
    }

    @Override public void update(Product product) {
        products.replaceAll(p -> p.getId().equals(product.getId()) ? copy(product) : p);
        if (product.getPhoto() != null) photos.put(product.getId(), product.getPhoto());
    }

    @Override public void delete(long productId) {
        products.removeIf(p -> p.getId() == productId);
        photos.remove(productId);
    }

    @Override public boolean isUsed(long productId) { return usedInMenu.contains(productId); }

    @Override public byte[] loadPhoto(long productId) { return photos.get(productId); }

    @Override public List<PriceChange> findPriceHistory(long productId) { return List.of(); }

    public Product stored(long id) { return products.stream().filter(p -> p.getId() == id).findFirst().orElse(null); }

    private static Product copy(Product p) {
        Product c = new Product(p.getName(), p.getDescription(), p.getUnit(), p.getCurrentPrice(), p.getVatRate(),
                p.getAllergenList());
        c.setId(p.getId());
        return c;
    }
}
