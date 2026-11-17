package business.domainmodel;

/**
 * CV07: the customer's billing address. Embedded Value: no ID, equal by value, stored as columns of the
 * customer record. Optional on the customer; if present, all four fields are required. Delivery addresses stay separate ({@link Address}, loaded lazily).
 */
public record BillingAddress(String street, String city, String zipCode, String country) {
    public BillingAddress {
        if (Customer.isBlank(street) || Customer.isBlank(city) || Customer.isBlank(zipCode)
                || Customer.isBlank(country)) {
            throw new IllegalArgumentException("Fakturační adresa musí mít ulici, město, PSČ a stát.");
        }
    }
}
