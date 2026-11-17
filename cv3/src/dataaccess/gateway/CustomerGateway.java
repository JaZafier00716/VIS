package dataaccess.gateway;

import dataaccess.dto.CustomerDto;

import java.util.List;
import java.util.Optional;

/**
 * CTI: Table Data Gateway used by the Data Mapper (table customers: the common attributes of every customer
 * and the discriminator customer_type; DTOs in and out, no state).
 */
public interface CustomerGateway {
    void insert(CustomerDto record);

    void update(CustomerDto record);

    void delete(String id);

    Optional<CustomerDto> findById(String id);

    List<CustomerDto> findAll();
}
