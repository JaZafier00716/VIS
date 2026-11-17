package dataaccess.gateway;

import dataaccess.dto.StiCustomerDto;

import java.util.List;
import java.util.Optional;

/**
 * STI: Table Data Gateway used by the Data Mapper (table customers_sti: both subtypes, discriminator
 * customer_type; DTOs in and out, no state).
 */
public interface StiCustomerGateway {
    void insert(StiCustomerDto record);

    void update(StiCustomerDto record);

    void delete(String id);

    Optional<StiCustomerDto> findById(String id);

    List<StiCustomerDto> findAll();
}
