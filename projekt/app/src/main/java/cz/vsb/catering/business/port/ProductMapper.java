package cz.vsb.catering.business.port;

import cz.vsb.catering.business.domain.Allergen;
import cz.vsb.catering.business.domain.PriceChange;
import cz.vsb.catering.business.domain.Product;
import java.util.List;

public interface ProductMapper {
    /** Catalogue ordered by name, with allergen names. */
    List<Product> findAll();

    /** The 14 allergens ordered by id. */
    List<Allergen> findAllergens();

    /** Inserts the product with its allergens and photo (when set) and sets the id. */
    void insert(Product product);

    /** Updates the data, replaces the allergens and stores the photo when set (else keeps it). */
    void update(Product product);

    /** Deletes the product; allergens and price history cascade. */
    void delete(long productId);

    /** True when a menu item uses the product (FK fk_menu_items_product blocks the delete). */
    boolean isUsed(long productId);

    /** Lazy Load of the photo; null when the product has none. */
    byte[] loadPhoto(long productId);

    /** Price journal of the product, newest first. */
    List<PriceChange> findPriceHistory(long productId);
}
