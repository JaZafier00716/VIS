package configuration;

import java.util.Locale;

/**
 * CV07: how the Customer hierarchy is stored, chosen by {@code -Dvis.customerMapping=cti|sti} or
 * {@code --mapping=cti|sti} (default CTI). The user chooses person or company; the mapping is a deployment
 * decision of the data access layer (cv07 p.15), so the business layer never sees it.
 */
public enum CustomerMapping {
    /** Class Table Inheritance: customers + person_customers + company_customers. */
    CTI,
    /** Single Table Inheritance: customers_sti with the customer_type discriminator. */
    STI;

    public static CustomerMapping parse(String value) {
        if (value == null || value.isBlank()) {
            return CTI;
        }
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Neznámé mapování dědičnosti '" + value
                    + "', použijte cti nebo sti.");
        }
    }

    public static CustomerMapping fromSystemProperty() {
        return parse(System.getProperty("vis.customerMapping"));
    }

    /** Each strategy has its own store: the H2 file {@code vis-h2-cti} / the JSON folder {@code json-cti}. */
    String suffix() {
        return name().toLowerCase(Locale.ROOT);
    }
}
