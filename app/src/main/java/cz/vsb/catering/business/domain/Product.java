package cz.vsb.catering.business.domain;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Catalogue product (dish or drink) with a default price without VAT, its allergens and an optional
 *  photo (UC06 Spravovat katalog produktů). */
public class Product extends DomainObject {
    public static final Set<String> UNITS = Set.of("pcs", "portion", "kg", "l", "set");
    /** Czech labels of the unit codes stored in products.unit (shown in every UI and the PDF). */
    public static final java.util.Map<String, String> UNIT_LABELS =
            java.util.Map.of("pcs", "ks", "portion", "porce", "kg", "kg", "l", "l", "set", "sada");
    public static final Set<BigDecimal> VAT_RATES = Set.of(BigDecimal.ZERO, new BigDecimal("12"), new BigDecimal("21"));
    public static final int MAX_PHOTO_BYTES = 100 * 1024;

    private String name;
    private String description;
    private String unit;
    private Money currentPrice;
    private BigDecimal vatRate;
    private List<Allergen> allergens;
    private byte[] photo;                       // only a new photo to store; reading is lazy (loadPhoto)

    public Product(String name, String description, String unit, Money currentPrice, BigDecimal vatRate,
                   List<Allergen> allergens) {
        this.name = name;
        this.description = description;
        this.unit = unit;
        this.currentPrice = currentPrice;
        this.vatRate = vatRate;
        this.allergens = List.copyOf(allergens);
    }

    /** New product with validated data (UC06). */
    public static Product create(String name, String description, String unit, BigDecimal price, BigDecimal vatRate,
                                 List<Allergen> allergens, byte[] photo) {
        Product p = new Product(null, null, null, null, null, List.of());
        p.update(name, description, unit, price, vatRate, allergens, photo);
        return p;
    }

    /** Validates and changes the data; photo null keeps the stored photo. */
    public void update(String name, String description, String unit, BigDecimal price, BigDecimal vatRate,
                       List<Allergen> allergens, byte[] photo) {
        Map<String, String> errors = new LinkedHashMap<>();
        name = Customer.trim(name);
        description = Customer.trim(description);
        if (name == null) errors.put("name", "Název je povinný.");
        else if (name.length() > 100) errors.put("name", "Název má nejvýše 100 znaků.");
        if (description != null && description.length() > 500) errors.put("description", "Popis má nejvýše 500 znaků.");
        if (unit == null || !UNITS.contains(unit)) errors.put("unit", "Vyberte jednotku.");
        if (price == null || price.signum() < 0) errors.put("price", "Cena musí být číslo 0 nebo větší.");
        else if (price.stripTrailingZeros().scale() > 2 || price.compareTo(new BigDecimal("100000000")) >= 0)
            errors.put("price", "Cena má nejvýše 2 desetinná místa a je menší než 100 000 000.");
        if (vatRate == null || VAT_RATES.stream().noneMatch(v -> v.compareTo(vatRate) == 0))
            errors.put("vatRate", "Sazba DPH je 0, 12 nebo 21 %.");
        if (photo != null && photo.length > MAX_PHOTO_BYTES) errors.put("photo", "Fotografie má nejvýše 100 kB.");
        ValidationException.throwIfAny(errors);
        this.name = name;
        this.description = description;
        this.unit = unit;
        this.currentPrice = Money.of(price);
        this.vatRate = vatRate;
        this.allergens = allergens.stream().distinct().sorted(Comparator.comparingInt(Allergen::id)).toList();
        this.photo = photo;
    }

    public String getName() { return name; }
    public String getDescription() { return description; }
    public String getUnit() { return unit; }
    public String getUnitLabel() { return UNIT_LABELS.getOrDefault(unit, unit); }
    public Money getCurrentPrice() { return currentPrice; }
    public BigDecimal getVatRate() { return vatRate; }
    /** Allergen names (menu editor, web, PDF). */
    public List<String> getAllergens() { return allergens.stream().map(Allergen::name).toList(); }
    public List<Allergen> getAllergenList() { return allergens; }
    public byte[] getPhoto() { return photo; }

    @Override public String toString() { return name; }
}
