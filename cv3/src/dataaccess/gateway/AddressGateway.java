package dataaccess.gateway;

import dataaccess.dto.AddressDto;

import java.util.List;
import java.util.Optional;

/** Table Data Gateway used by the Data Mapper (table addresses; DTOs in and out, no state). */
public interface AddressGateway {
    void insert(AddressDto record);

    void update(AddressDto record);

    void delete(String id);

    Optional<AddressDto> findById(String id);

    List<AddressDto> findAll();

    /** Foreign Key Mapping read from the other side: all addresses whose customer_id matches. */
    List<AddressDto> findByCustomerId(String customerId);
}
