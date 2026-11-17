package business.repository;

import business.domainmodel.Order;

import java.util.List;
import java.util.Optional;

/** Data Mapper for orders with their items; the customer is loaded lazily on first access. */
public interface OrderRepository {
    String nextId();

    Optional<Order> findById(String orderId);

    List<Order> findAll();

    void add(Order order);

    void update(Order order);

    void remove(Order order);
}
