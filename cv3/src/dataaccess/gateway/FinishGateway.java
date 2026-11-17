package dataaccess.gateway;

import dataaccess.dto.FinishDto;

import java.util.List;
import java.util.Optional;

/** Table Data Gateway used by the Data Mapper (table finishes; DTOs in and out, no state). */
public interface FinishGateway {
    void insert(FinishDto record);

    void update(FinishDto record);

    void delete(String id);

    Optional<FinishDto> findById(String id);

    List<FinishDto> findAll();
}
