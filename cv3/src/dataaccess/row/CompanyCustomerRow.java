package dataaccess.row;

/** JSON record of {@code company_customers.json} (CTI). */
public record CompanyCustomerRow(String customerId, String companyName, String ico, String dic,
                                 String contactPerson) {}
