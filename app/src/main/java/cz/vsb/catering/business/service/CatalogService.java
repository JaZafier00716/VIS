package cz.vsb.catering.business.service;

import cz.vsb.catering.business.domain.Allergen;
import cz.vsb.catering.business.domain.PriceChange;
import cz.vsb.catering.business.domain.Product;
import cz.vsb.catering.business.domain.ValidationException;
import cz.vsb.catering.business.port.ProductMapper;
import cz.vsb.catering.business.port.TransactionRunner;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/** Facade for UC06 Spravovat katalog produktů «CRUD». */
public class CatalogService {
    private final ProductMapper products;
    private final TransactionRunner tx;

    public CatalogService(ProductMapper products, TransactionRunner tx) {
        this.products = products;
        this.tx = tx;
    }

    public List<Product> findProducts() { return tx.inTransaction(products::findAll); }

    public List<Allergen> findAllergens() { return tx.inTransaction(products::findAllergens); }

    /** Lazy Load of the photo; null when there is none. */
    public byte[] loadPhoto(long productId) { return tx.inTransaction(() -> products.loadPhoto(productId)); }

    /** Previous prices journaled by the DB trigger, newest first. */
    public List<PriceChange> findPriceHistory(long productId) {
        return tx.inTransaction(() -> products.findPriceHistory(productId));
    }

    /** photo may be null (no photo). */
    public Product create(String name, String description, String unit, BigDecimal price, BigDecimal vatRate,
                          List<Allergen> allergens, byte[] photo) {
        Product product = Product.create(name, description, unit, price, vatRate, allergens, photo);
        return tx.inTransaction(() -> {
            requireUniqueName(product);
            products.insert(product);
            return product;
        });
    }

    /** photo null keeps the stored photo. */
    public Product update(long productId, String name, String description, String unit, BigDecimal price,
                          BigDecimal vatRate, List<Allergen> allergens, byte[] photo) {
        return tx.inTransaction(() -> {
            Product product = products.findAll().stream().filter(p -> p.getId() == productId).findFirst()
                    .orElseThrow(() -> new ValidationException("Produkt neexistuje."));
            product.update(name, description, unit, price, vatRate, allergens, photo);
            requireUniqueName(product);
            products.update(product);
            return product;
        });
    }

    /** A product used in a menu cannot be deleted (saved revisions must stay complete). */
    public void delete(long productId) {
        tx.inTransaction(() -> {
            if (products.isUsed(productId))
                throw new ValidationException("Produkt nelze smazat, protože je použit v menu.");
            products.delete(productId);
        });
    }

    private void requireUniqueName(Product product) {
        boolean taken = products.findAll().stream().anyMatch(p ->
                product.getName().equalsIgnoreCase(p.getName()) && !p.getId().equals(product.getId()));
        if (taken) {
            String msg = "Produkt „" + product.getName() + "“ již v katalogu existuje.";
            throw new ValidationException(msg, Map.of("name", msg));
        }
    }
}
