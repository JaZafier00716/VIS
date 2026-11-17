package dataaccess.row;

/** JSON record of {@code customers.json} (CTI common table). */
public record CustomerRow(String customerId, String customerType, String email, String phone,
                          String billingStreet, String billingCity, String billingZipCode, String billingCountry) {}
