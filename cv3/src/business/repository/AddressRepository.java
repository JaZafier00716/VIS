package business.repository;

import business.domainmodel.Address;

import java.util.List;

/** Data Mapper for addresses; a customer's addresses are normally reached lazily through the customer. */
public interface AddressRepository {
    String nextId();

    List<Address> findByCustomerId(String customerId);

    void add(Address address);
}
