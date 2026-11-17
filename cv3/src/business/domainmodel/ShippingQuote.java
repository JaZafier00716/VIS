package business.domainmodel;

/** CV08 (cv08 p.15–16): the result of a shipping calculation in the application's own terms. */
public record ShippingQuote(Money price, int deliveryDays) {
    public ShippingQuote {
        if (price == null) {
            throw new IllegalArgumentException("Cena dopravy je povinná.");
        }
        if (deliveryDays < 0) {
            throw new IllegalArgumentException("Doba doručení nesmí být záporná.");
        }
    }
}
