package cz.vsb.catering.business.port;

import cz.vsb.catering.business.domain.Customer;
import java.util.List;
import java.util.Optional;

public interface CustomerMapper {
    List<Customer> findAll();

    Optional<Customer> findById(long customerId);

    /** Inserts the customer and sets the id. */
    void insert(Customer customer);

    void update(Customer customer);

    void delete(long customerId);

    /** Number of projects of the customer (FK fk_projects_customer blocks the delete). */
    int countProjects(long customerId);
}
