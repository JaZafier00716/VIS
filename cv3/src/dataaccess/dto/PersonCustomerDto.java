package dataaccess.dto;

import java.time.LocalDate;

/** The PersonCustomer part; in CTI one row of {@code person_customers} (customer_id = PK + FK). */
public record PersonCustomerDto(String customerId, String firstName, String lastName, LocalDate birthDate) {}
