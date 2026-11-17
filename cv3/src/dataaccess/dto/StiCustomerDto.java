package dataaccess.dto;

import java.time.LocalDate;

/**
 * STI: one row of {@code customers_sti}. {@code customerType} is the discriminator (P = person, C = company);
 * the other subtype's columns are null.
 */
public record StiCustomerDto(String customerId, String customerType, String email, String phone,
                             String billingStreet, String billingCity, String billingZipCode, String billingCountry,
                             String firstName, String lastName, LocalDate birthDate,
                             String companyName, String ico, String dic, String contactPerson) {}
