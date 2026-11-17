package dataaccess.row;

/** JSON record of {@code customers_sti.json}: both subtypes, discriminator customerType P/C. */
public record StiCustomerRow(String customerId, String customerType, String email, String phone,
                             String billingStreet, String billingCity, String billingZipCode, String billingCountry,
                             String firstName, String lastName, String birthDate,
                             String companyName, String ico, String dic, String contactPerson) {}
