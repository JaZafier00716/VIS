package dataaccess.gateway;

import dataaccess.dto.MaterialDto;

import java.util.List;
import java.util.Optional;

/** Table Data Gateway used by the Data Mapper (table materials; DTOs in and out, no state). */
public interface MaterialGateway {
    void insert(MaterialDto record);

    void update(MaterialDto record);

    void delete(String id);

    Optional<MaterialDto> findById(String id);

    List<MaterialDto> findAll();
}
