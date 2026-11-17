package dataaccess.mapper.customer;

import business.domainmodel.CompanyCustomer;
import business.domainmodel.Customer;
import business.domainmodel.PersonCustomer;
import dataaccess.dto.CompanyCustomerDto;
import dataaccess.dto.CustomerDto;
import dataaccess.dto.PersonCustomerDto;
import dataaccess.dto.StiCustomerDto;
import dataaccess.gateway.StiCustomerGateway;

import java.util.List;
import java.util.Optional;

/**
 * Single Table Inheritance (cv07 p.5–6): the common part and the specific part go into one row of
 * {@code customers_sti}; {@code customer_type} (P/C) records the subtype and the other subtype's columns stay
 * NULL. On load the discriminator decides which class is built.
 */
public class StiCustomerTables implements CustomerTables {
    private static final PersonCustomerMapper PERSON = PersonCustomerMapper.INSTANCE;
    private static final CompanyCustomerMapper COMPANY = CompanyCustomerMapper.INSTANCE;
    private final StiCustomerGateway gateway;

    public StiCustomerTables(StiCustomerGateway gateway) {
        this.gateway = gateway;
    }

    @Override
    public void insert(Customer customer) {
        gateway.insert(toRow(customer));
    }

    @Override
    public void update(Customer customer) {
        gateway.update(toRow(customer));
    }

    @Override
    public void delete(String customerId) {
        gateway.delete(customerId);
    }

    @Override
    public Optional<Customer> findById(String customerId) {
        return gateway.findById(customerId).map(StiCustomerTables::toDomain);
    }

    @Override
    public List<Customer> findAll() {
        return gateway.findAll().stream().map(StiCustomerTables::toDomain).toList();
    }

    @Override
    public List<String> tablesOf(String customerId) {
        return gateway.findById(customerId).isPresent() ? List.of("customers_sti") : List.of();
    }

    private static StiCustomerDto toRow(Customer customer) {
        return switch (customer) {
            case PersonCustomer person -> row(PERSON.toCommon(person), PERSON.toPart(person), null);
            case CompanyCustomer company -> row(COMPANY.toCommon(company), null, COMPANY.toPart(company));
            default -> throw new IllegalArgumentException("Nepodporovaný typ zákazníka: "
                    + customer.getClass().getName());
        };
    }

    /** One row from the common part and exactly one specific part; the other part's columns are NULL. */
    private static StiCustomerDto row(CustomerDto c, PersonCustomerDto p, CompanyCustomerDto f) {
        return new StiCustomerDto(c.customerId(), c.customerType(), c.email(), c.phone(), c.billingStreet(),
                c.billingCity(), c.billingZipCode(), c.billingCountry(),
                p == null ? null : p.firstName(), p == null ? null : p.lastName(), p == null ? null : p.birthDate(),
                f == null ? null : f.companyName(), f == null ? null : f.ico(), f == null ? null : f.dic(),
                f == null ? null : f.contactPerson());
    }

    /** The discriminator decides the class (cv07 p.6 step 5). */
    private static Customer toDomain(StiCustomerDto r) {
        CustomerDto common = new CustomerDto(r.customerId(), r.customerType(), r.email(), r.phone(),
                r.billingStreet(), r.billingCity(), r.billingZipCode(), r.billingCountry());
        return switch (r.customerType()) {
            case CustomerDto.PERSON -> PERSON.toDomain(common,
                    new PersonCustomerDto(r.customerId(), r.firstName(), r.lastName(), r.birthDate()));
            case CustomerDto.COMPANY -> COMPANY.toDomain(common,
                    new CompanyCustomerDto(r.customerId(), r.companyName(), r.ico(), r.dic(), r.contactPerson()));
            default -> throw new IllegalStateException("Neznámý customer_type " + r.customerType() + " zákazníka "
                    + r.customerId() + ".");
        };
    }
}
