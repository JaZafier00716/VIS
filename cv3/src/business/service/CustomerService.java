package business.service;

import business.domainmodel.Address;
import business.domainmodel.BillingAddress;
import business.domainmodel.CompanyCustomer;
import business.domainmodel.Customer;
import business.domainmodel.PersonCustomer;
import business.repository.AddressRepository;
import business.repository.CustomerRepository;
import business.repository.UnitOfWorkControl;

import java.time.LocalDate;
import java.util.List;

public class CustomerService {
    private final CustomerRepository customers;
    private final AddressRepository addresses;
    private final UnitOfWorkControl unitOfWork;

    public CustomerService(CustomerRepository customers, AddressRepository addresses, UnitOfWorkControl unitOfWork) {
        this.customers = customers;
        this.addresses = addresses;
        this.unitOfWork = unitOfWork;
    }

    /** CV07: creates a natural person; {@code id} may be null to generate one. */
    public PersonCustomer createPersonCustomer(String id, String email, String phone, BillingAddress billingAddress,
                                               String firstName, String lastName, LocalDate birthDate) {
        PersonCustomer customer = newPersonCustomer(id, email, phone, billingAddress, firstName, lastName, birthDate);
        customers.add(customer);
        commit();
        return customer;
    }

    /** CV07: creates a company; {@code id} may be null to generate one. */
    public CompanyCustomer createCompanyCustomer(String id, String email, String phone, BillingAddress billingAddress,
                                                 String companyName, String ico, String dic, String contactPerson) {
        CompanyCustomer customer = newCompanyCustomer(id, email, phone, billingAddress, companyName, ico, dic,
                contactPerson);
        customers.add(customer);
        commit();
        return customer;
    }

    /** Builds and validates a new person (domain rules in the class itself); registers and commits nothing. */
    PersonCustomer newPersonCustomer(String id, String email, String phone, BillingAddress billingAddress,
                                     String firstName, String lastName, LocalDate birthDate) {
        PersonCustomer customer = new PersonCustomer(idOrNext(id), email, phone, billingAddress, firstName, lastName,
                birthDate);
        customer.validate();
        return customer;
    }

    /** Builds and validates a new company; registers and commits nothing. */
    CompanyCustomer newCompanyCustomer(String id, String email, String phone, BillingAddress billingAddress,
                                       String companyName, String ico, String dic, String contactPerson) {
        CompanyCustomer customer = new CompanyCustomer(idOrNext(id), email, phone, billingAddress, companyName, ico,
                dic, contactPerson);
        customer.validate();
        return customer;
    }

    private String idOrNext(String id) {
        return id == null || id.isBlank() ? customers.nextId() : id;
    }

    /** The repository returns the Identity Map instance; the addresses are loaded lazily by the data layer. */
    public Customer findCustomer(String id) {
        return customers.findById(id).orElseThrow(() -> new IllegalArgumentException("Zákazník " + id + " neexistuje."));
    }

    public List<Customer> findAll() {
        return customers.findAll();
    }

    public Address addAddress(String customerId, String street, String city, String zipCode, String country) {
        Customer customer = findCustomer(customerId);
        Address address = newAddress(customerId, street, city, zipCode, country);
        customer.addAddress(address);
        addresses.add(address);
        commit();
        return address;
    }

    /** Validates the input and builds a new address of the customer; registers and commits nothing. */
    Address newAddress(String customerId, String street, String city, String zipCode, String country) {
        Address address = Address.builder().addressId(addresses.nextId()).customerId(customerId).street(street)
                .city(city).zipCode(zipCode).country(country).build();
        address.validate();
        return address;
    }

    public void deleteCustomer(String customerId) {
        customers.remove(findCustomer(customerId));
        commit();
    }

    private void commit() {
        try {
            unitOfWork.commit();
        } catch (RuntimeException e) {
            unitOfWork.rollback();
            throw e;
        }
    }
}
