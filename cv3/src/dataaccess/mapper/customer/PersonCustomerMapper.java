package dataaccess.mapper.customer;

import business.domainmodel.PersonCustomer;
import dataaccess.dto.CustomerDto;
import dataaccess.dto.PersonCustomerDto;

/** Inheritance Mappers (cv07 p.13), the PersonCustomer part: first name, last name, birth date. */
public final class PersonCustomerMapper extends CustomerInheritanceMapper<PersonCustomer, PersonCustomerDto> {
    public static final PersonCustomerMapper INSTANCE = new PersonCustomerMapper();

    private PersonCustomerMapper() {}

    @Override
    public String type() {
        return CustomerDto.PERSON;
    }

    @Override
    public PersonCustomerDto toPart(PersonCustomer customer) {
        return new PersonCustomerDto(customer.getCustomerId(), customer.getFirstName(), customer.getLastName(),
                customer.getBirthDate());
    }

    @Override
    public PersonCustomer toDomain(CustomerDto common, PersonCustomerDto part) {
        return new PersonCustomer(common.customerId(), common.email(), common.phone(), billingAddress(common),
                part.firstName(), part.lastName(), part.birthDate());
    }
}
