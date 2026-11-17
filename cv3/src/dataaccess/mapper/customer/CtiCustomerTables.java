package dataaccess.mapper.customer;

import business.domainmodel.CompanyCustomer;
import business.domainmodel.Customer;
import business.domainmodel.PersonCustomer;
import dataaccess.dto.CompanyCustomerDto;
import dataaccess.dto.CustomerDto;
import dataaccess.dto.PersonCustomerDto;
import dataaccess.gateway.CompanyCustomerGateway;
import dataaccess.gateway.CustomerGateway;
import dataaccess.gateway.PersonCustomerGateway;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Class Table Inheritance (cv07 p.7–8): the common part goes to {@code customers}, the specific part to
 * {@code person_customers} or {@code company_customers} under the same ID.
 *
 * <ul>
 *   <li>Insert: {@code customers} first, then the subtype table. Both writes run inside the Unit of Work's one
 *       transaction (one JDBC connection on H2, the file journal on JSON), so a failing subtype insert leaves no
 *       orphan row in {@code customers}.</li>
 *   <li>Load: {@code customers}, then the subtype tables; the instance is built from both parts.</li>
 *   <li>Update and delete handle both rows.</li>
 *   <li>Exactly one subtype row per customer (cv07 p.8: the FK alone does not guarantee it): insert refuses an ID
 *       that already has a row in the other subtype table, and load refuses a customer with no subtype row, with
 *       rows in both, or with a row that does not match {@code customer_type}.</li>
 * </ul>
 */
public class CtiCustomerTables implements CustomerTables {
    private static final PersonCustomerMapper PERSON = PersonCustomerMapper.INSTANCE;
    private static final CompanyCustomerMapper COMPANY = CompanyCustomerMapper.INSTANCE;
    private final CustomerGateway customers;
    private final PersonCustomerGateway persons;
    private final CompanyCustomerGateway companies;

    public CtiCustomerTables(CustomerGateway customers, PersonCustomerGateway persons,
                             CompanyCustomerGateway companies) {
        this.customers = customers;
        this.persons = persons;
        this.companies = companies;
    }

    @Override
    public void insert(Customer customer) {
        String id = customer.getCustomerId();
        switch (customer) {
            case PersonCustomer person -> {
                requireNoRow(companies.findById(id), id, "company_customers");
                customers.insert(PERSON.toCommon(person));
                persons.insert(PERSON.toPart(person));
            }
            case CompanyCustomer company -> {
                requireNoRow(persons.findById(id), id, "person_customers");
                customers.insert(COMPANY.toCommon(company));
                companies.insert(COMPANY.toPart(company));
            }
            default -> throw unsupported(customer);
        }
    }

    @Override
    public void update(Customer customer) {
        switch (customer) {
            case PersonCustomer person -> {
                customers.update(PERSON.toCommon(person));
                persons.update(PERSON.toPart(person));
            }
            case CompanyCustomer company -> {
                customers.update(COMPANY.toCommon(company));
                companies.update(COMPANY.toPart(company));
            }
            default -> throw unsupported(customer);
        }
    }

    /** The subtype row first, then the common row; orders still block the delete of the common row. */
    @Override
    public void delete(String customerId) {
        persons.delete(customerId);
        companies.delete(customerId);
        customers.delete(customerId);
    }

    @Override
    public Optional<Customer> findById(String customerId) {
        return customers.findById(customerId).map(this::assemble);
    }

    @Override
    public List<Customer> findAll() {
        return customers.findAll().stream().map(this::assemble).toList();
    }

    @Override
    public List<String> tablesOf(String customerId) {
        List<String> tables = new ArrayList<>();
        if (customers.findById(customerId).isPresent()) {
            tables.add("customers");
        }
        if (persons.findById(customerId).isPresent()) {
            tables.add("person_customers");
        }
        if (companies.findById(customerId).isPresent()) {
            tables.add("company_customers");
        }
        return tables;
    }

    private Customer assemble(CustomerDto common) {
        String id = common.customerId();
        Optional<PersonCustomerDto> person = persons.findById(id);
        Optional<CompanyCustomerDto> company = companies.findById(id);
        if (person.isPresent() == company.isPresent()) {
            throw new IllegalStateException("CTI: zákazník " + id + " musí mít právě jeden záznam potomka, má "
                    + (person.isPresent() ? "dva" : "žádný") + ".");
        }
        if (person.isPresent() && CustomerDto.PERSON.equals(common.customerType())) {
            return PERSON.toDomain(common, person.get());
        }
        if (company.isPresent() && CustomerDto.COMPANY.equals(common.customerType())) {
            return COMPANY.toDomain(common, company.get());
        }
        throw new IllegalStateException("CTI: customer_type " + common.customerType() + " zákazníka " + id
                + " neodpovídá tabulce potomka.");
    }

    private static void requireNoRow(Optional<?> otherSubtypeRow, String id, String table) {
        if (otherSubtypeRow.isPresent()) {
            throw new IllegalStateException("CTI: zákazník " + id + " už má záznam v " + table + ".");
        }
    }

    private static IllegalArgumentException unsupported(Customer customer) {
        return new IllegalArgumentException("Nepodporovaný typ zákazníka: " + customer.getClass().getName());
    }
}
