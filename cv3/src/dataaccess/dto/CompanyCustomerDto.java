package dataaccess.dto;

/** The CompanyCustomer part; in CTI one row of {@code company_customers} (customer_id = PK + FK). */
public record CompanyCustomerDto(String customerId, String companyName, String ico, String dic,
                                 String contactPerson) {}
