package dataaccess.gateway;

import dataaccess.dto.CompanyCustomerDto;

import java.util.Optional;

/**
 * CTI: Table Data Gateway used by the Data Mapper (table company_customers, customer_id = PK + FK to customers;
 * DTOs in and out, no state).
 */
public interface CompanyCustomerGateway {
    void insert(CompanyCustomerDto record);

    void update(CompanyCustomerDto record);

    void delete(String id);

    Optional<CompanyCustomerDto> findById(String id);
}
