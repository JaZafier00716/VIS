package dataaccess.gateway;

import dataaccess.dto.PersonCustomerDto;

import java.util.Optional;

/**
 * CTI: Table Data Gateway used by the Data Mapper (table person_customers, customer_id = PK + FK to customers;
 * DTOs in and out, no state).
 */
public interface PersonCustomerGateway {
    void insert(PersonCustomerDto record);

    void update(PersonCustomerDto record);

    void delete(String id);

    Optional<PersonCustomerDto> findById(String id);
}
