package business.domainmodel;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * The life cycle of an order (CV09). The constants are stored by name, so {@code CEKA_NA_POTVRZENI} keeps its
 * name for existing data although it is shown as "Nová". The allowed transitions live here and only here:
 * each status may change only to the next one in the declared order.
 */
@Getter
@RequiredArgsConstructor
public enum OrderStatus {
    CEKA_NA_POTVRZENI("Nová"),
    POTVRZENA("Potvrzená"),
    VE_VYROBE("Ve výrobě"),
    PRIPRAVENA_K_EXPEDICI("Připravena k expedici"),
    EXPEDOVANA("Expedovaná"),
    UZAVRENA("Uzavřená");

    private final String label;

    /** The only status this one may change to, or {@code null} for the final status. */
    public OrderStatus next() {
        OrderStatus[] all = values();
        return ordinal() + 1 < all.length ? all[ordinal() + 1] : null;
    }

    public boolean canChangeTo(OrderStatus target) {
        return target != null && target == next();
    }
}
