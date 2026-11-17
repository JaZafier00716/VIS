package dataaccess.row;

/** JSON record of {@code person_customers.json} (CTI). */
public record PersonCustomerRow(String customerId, String firstName, String lastName, String birthDate) {}
