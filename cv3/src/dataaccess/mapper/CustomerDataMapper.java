package dataaccess.mapper;

import business.domainmodel.Customer;
import business.repository.CustomerRepository;
import dataaccess.mapper.customer.CustomerTables;
import dataaccess.identity.IdGenerator;
import dataaccess.unitofwork.UnitOfWork;

import java.util.List;
import java.util.Optional;

/**
 * Data Mapper for customers of either subtype. The inheritance strategy ({@link CustomerTables}: CTI or STI)
 * builds the right subclass from its tables (cv07 p.14); this class adds the Identity Map and Lazy Load.
 * Identity Map: every finder returns the instance already loaded in this unit of work.
 * Lazy Load: the addresses are attached as a loader and read on the first {@code getAddresses()}.
 */
public class CustomerDataMapper implements CustomerRepository {
    private final CustomerTables tables;
    private final AddressDataMapper addresses;
    private final UnitOfWork unitOfWork;

    public CustomerDataMapper(CustomerTables tables, AddressDataMapper addresses, UnitOfWork unitOfWork) {
        this.tables = tables;
        this.addresses = addresses;
        this.unitOfWork = unitOfWork;
    }

    @Override
    public String nextId() {
        return IdGenerator.assign(null, "C");
    }

    @Override
    public Optional<Customer> findById(String customerId) {
        return unitOfWork.get(Customer.class, customerId).or(() -> tables.findById(customerId).map(this::attach));
    }

    /** Records already in the Identity Map keep their loaded instance. */
    @Override
    public List<Customer> findAll() {
        return tables.findAll().stream()
                .map(loaded -> unitOfWork.get(Customer.class, loaded.getCustomerId()).orElseGet(() -> attach(loaded)))
                .toList();
    }

    @Override
    public void add(Customer customer) {
        unitOfWork.registerNew(customer, customer.getCustomerId());
    }

    @Override
    public void remove(Customer customer) {
        unitOfWork.registerDeleted(customer, customer.getCustomerId());
    }

    private Customer attach(Customer customer) {
        String id = customer.getCustomerId();
        customer.setAddressesLoader(() -> addresses.findByCustomerId(id));
        unitOfWork.remember(customer, id);
        return customer;
    }
}
