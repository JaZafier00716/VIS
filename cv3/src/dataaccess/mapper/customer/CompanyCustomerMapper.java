package dataaccess.mapper.customer;

import business.domainmodel.CompanyCustomer;
import dataaccess.dto.CompanyCustomerDto;
import dataaccess.dto.CustomerDto;

/** Inheritance Mappers (cv07 p.13), the CompanyCustomer part: company name, IČO, DIČ, contact person. */
public final class CompanyCustomerMapper extends CustomerInheritanceMapper<CompanyCustomer, CompanyCustomerDto> {
    public static final CompanyCustomerMapper INSTANCE = new CompanyCustomerMapper();

    private CompanyCustomerMapper() {}

    @Override
    public String type() {
        return CustomerDto.COMPANY;
    }

    @Override
    public CompanyCustomerDto toPart(CompanyCustomer customer) {
        return new CompanyCustomerDto(customer.getCustomerId(), customer.getCompanyName(), customer.getIco(),
                customer.getDic(), customer.getContactPerson());
    }

    @Override
    public CompanyCustomer toDomain(CustomerDto common, CompanyCustomerDto part) {
        return new CompanyCustomer(common.customerId(), common.email(), common.phone(), billingAddress(common),
                part.companyName(), part.ico(), part.dic(), part.contactPerson());
    }
}
