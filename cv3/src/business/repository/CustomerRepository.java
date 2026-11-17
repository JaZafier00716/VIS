package business.repository;

import business.domainmodel.Customer;

import java.util.List;
import java.util.Optional;

/**
 * Data Mapper seen from the business layer (cv04 "Kroky implementace DM", step 2): domain objects only.
 * Reads go through the Identity Map; add/remove register the change in the Unit of Work.
 */
public interface CustomerRepository {
    /** A new Identity Field value. */
    String nextId();

    Optional<Customer> findById(String customerId);

    List<Customer> findAll();

    void add(Customer customer);

    void remove(Customer customer);
}
