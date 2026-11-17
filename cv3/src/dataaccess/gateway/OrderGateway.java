package dataaccess.gateway;

import dataaccess.dto.OrderDto;

import java.util.List;
import java.util.Optional;

/** Table Data Gateway used by the Data Mapper (tables orders + dependent order_items; DTOs in and out, no state). */
public interface OrderGateway {
    void insert(OrderDto record);

    void update(OrderDto record);

    void delete(String id);

    Optional<OrderDto> findById(String id);

    List<OrderDto> findAll();
}
