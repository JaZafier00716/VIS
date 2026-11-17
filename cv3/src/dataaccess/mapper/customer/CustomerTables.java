package dataaccess.mapper.customer;

import business.domainmodel.Customer;

import java.util.List;
import java.util.Optional;

/**
 * How the Customer hierarchy is laid out in tables: one implementation per inheritance strategy
 * ({@link CtiCustomerTables}, {@link StiCustomerTables}). Internal to the data access layer (cv07 p.13: "společné
 * rozhraní a více implementací pro různé strategie"); the business layer still sees only
 * {@code business.repository.CustomerRepository}. Takes and returns domain objects of either subtype.
 */
public interface CustomerTables {
    void insert(Customer customer);

    void update(Customer customer);

    void delete(String customerId);

    Optional<Customer> findById(String customerId);

    List<Customer> findAll();

    /**
     * Diagnostic aid for demonstrations only, not part of the business API: the names of the tables that hold a
     * row of this customer (cv07 p.17 "data jsou ve správných tabulkách"). Plain strings, no DAL types.
     */
    List<String> tablesOf(String customerId);
}
