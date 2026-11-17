package dataaccess.dto;

/**
 * The common part of a customer (cv07 p.13): ID, discriminator, e-mail, phone and the billing address flattened
 * into four columns (Embedded Value). In CTI this is one row of {@code customers}.
 */
public record CustomerDto(String customerId, String customerType, String email, String phone,
                          String billingStreet, String billingCity, String billingZipCode, String billingCountry) {
    public static final String PERSON = "P";
    public static final String COMPANY = "C";
}
