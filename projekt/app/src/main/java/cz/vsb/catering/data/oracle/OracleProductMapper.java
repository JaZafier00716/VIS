package cz.vsb.catering.data.oracle;

import cz.vsb.catering.business.domain.Allergen;
import cz.vsb.catering.business.domain.Money;
import cz.vsb.catering.business.domain.PriceChange;
import cz.vsb.catering.business.domain.Product;
import cz.vsb.catering.business.port.ProductMapper;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/** Data Mapper for the product catalogue (products, allergens, product_allergens, photo BLOB). */
public class OracleProductMapper implements ProductMapper {
    /** Product columns of alias p, allergens as "id:name" joined by '|'; reused by OracleMenuMapper. */
    static final String COLUMNS = """
            p.product_id, p.name AS product_name, p.description, p.unit, p.current_price, p.vat_rate,
            (SELECT LISTAGG(a.allergen_id || ':' || a.name, '|') WITHIN GROUP (ORDER BY a.allergen_id)
             FROM product_allergens pa JOIN allergens a ON a.allergen_id = pa.allergen_id
             WHERE pa.product_id = p.product_id) AS allergen_names
            """;

    @Override
    public List<Product> findAll() {
        return Jdbc.query("SELECT " + COLUMNS + " FROM products p ORDER BY p.name", OracleProductMapper::read);
    }

    @Override
    public List<Allergen> findAllergens() {
        return Jdbc.query("SELECT allergen_id, name FROM allergens ORDER BY allergen_id",
                rs -> new Allergen(rs.getInt("allergen_id"), rs.getString("name")));
    }

    @Override
    public void insert(Product p) {
        p.setId(Jdbc.insert("INSERT INTO products (name, description, unit, current_price, vat_rate) VALUES (?, ?, ?, ?, ?)",
                "product_id", p.getName(), p.getDescription(), p.getUnit(), p.getCurrentPrice().amount(), p.getVatRate()));
        saveAllergensAndPhoto(p);
    }

    @Override
    public void update(Product p) {
        // a price change is journaled by the trigger trg_products_price_history
        Jdbc.update("UPDATE products SET name = ?, description = ?, unit = ?, current_price = ?, vat_rate = ? WHERE product_id = ?",
                p.getName(), p.getDescription(), p.getUnit(), p.getCurrentPrice().amount(), p.getVatRate(), p.getId());
        Jdbc.update("DELETE FROM product_allergens WHERE product_id = ?", p.getId());
        saveAllergensAndPhoto(p);
    }

    private static void saveAllergensAndPhoto(Product p) {
        for (Allergen a : p.getAllergenList())
            Jdbc.update("INSERT INTO product_allergens (product_id, allergen_id) VALUES (?, ?)", p.getId(), a.id());
        if (p.getPhoto() != null) Jdbc.update("UPDATE products SET photo = ? WHERE product_id = ?", p.getPhoto(), p.getId());
    }

    @Override
    public void delete(long productId) {
        Jdbc.update("DELETE FROM products WHERE product_id = ?", productId);
    }

    @Override
    public boolean isUsed(long productId) {
        return !Jdbc.query("SELECT 1 FROM menu_items WHERE product_id = ? FETCH FIRST 1 ROWS ONLY", rs -> 1, productId).isEmpty();
    }

    @Override
    public byte[] loadPhoto(long productId) {
        List<byte[]> photo = Jdbc.query("SELECT photo FROM products WHERE product_id = ?", rs -> rs.getBytes("photo"), productId);
        return photo.isEmpty() ? null : photo.get(0);                    // the element itself is null without a photo
    }

    @Override
    public List<PriceChange> findPriceHistory(long productId) {
        return Jdbc.query("SELECT changed_at, old_price, new_price FROM product_price_history WHERE product_id = ? "
                        + "ORDER BY changed_at DESC, history_id DESC",
                rs -> new PriceChange(Jdbc.dateTime(rs, "changed_at"), Money.of(rs.getBigDecimal("old_price")),
                        Money.of(rs.getBigDecimal("new_price"))), productId);
    }

    static Product read(ResultSet rs) throws SQLException {
        String names = rs.getString("allergen_names");
        List<Allergen> allergens = new ArrayList<>();
        if (names != null)
            for (String a : names.split("\\|")) {
                int colon = a.indexOf(':');
                allergens.add(new Allergen(Integer.parseInt(a.substring(0, colon)), a.substring(colon + 1)));
            }
        Product p = new Product(rs.getString("product_name"), rs.getString("description"), rs.getString("unit"),
                Money.of(rs.getBigDecimal("current_price")), rs.getBigDecimal("vat_rate"), allergens);
        p.setId(rs.getLong("product_id"));
        return p;
    }
}
