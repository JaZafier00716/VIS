package business.domainmodel;

import lombok.Getter;

/**
 * Dependent object of {@link Order}. It has no owner ID and no public constructor:
 * items are created and changed only through the owning order.
 */
@Getter
public class OrderItem {
    private final int lineNumber;
    private final String materialId;
    private int quantity;
    private final Money unitPrice;
    private final ProductConfiguration configuration;

    OrderItem(int lineNumber, String materialId, int quantity, Money unitPrice,
              ProductConfiguration configuration) {
        if (lineNumber <= 0) {
            throw new IllegalArgumentException("Číslo položky musí být kladné.");
        }
        if (materialId == null || materialId.isBlank()) {
            throw new IllegalArgumentException("Položka musí mít materiál.");
        }
        if (unitPrice == null) {
            throw new IllegalArgumentException("Položka musí mít jednotkovou cenu.");
        }
        requirePositive(quantity);
        this.lineNumber = lineNumber;
        this.materialId = materialId;
        this.quantity = quantity;
        this.unitPrice = unitPrice;
        this.configuration = configuration;
    }

    /** CV08: the price of the whole line (Money × quantity). */
    public Money totalPrice() {
        return unitPrice.times(quantity);
    }

    void changeQuantity(int quantity) {
        requirePositive(quantity);
        this.quantity = quantity;
    }

    private static void requirePositive(int quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("Počet kusů musí být větší než 0.");
        }
    }
}
