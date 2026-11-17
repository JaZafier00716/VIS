package dataaccess.mapper.customer;

import business.domainmodel.BillingAddress;
import business.domainmodel.Customer;
import dataaccess.dto.CustomerDto;

import java.util.Arrays;
import java.util.Objects;

/**
 * Inheritance Mappers (cv07 p.13), the common part: maps what every {@link Customer} has (ID, e-mail, phone,
 * billing address) to the common record {@link CustomerDto} and back. The subclasses
 * ({@link PersonCustomerMapper}, {@link CompanyCustomerMapper}) add the specialised part. Neither part knows
 * tables: the strategy classes ({@link CtiCustomerTables}, {@link StiCustomerTables}) decide where the parts go.
 *
 * @param <T> the concrete customer class
 * @param <P> the record of its specific attributes
 */
public abstract class CustomerInheritanceMapper<T extends Customer, P> {
    /** The discriminator value: P = person, C = company (cv07 p.5). */
    public abstract String type();

    /** The specialised part: the subtype's own attributes. */
    public abstract P toPart(T customer);

    /** Builds the complete instance from the common record and the specialised part. */
    public abstract T toDomain(CustomerDto common, P part);

    /** The common part of any customer, with this mapper's discriminator. */
    public CustomerDto toCommon(T customer) {
        BillingAddress billing = customer.getBillingAddress();
        // Embedded Value: a missing billing address becomes four NULL columns.
        return new CustomerDto(customer.getCustomerId(), type(), customer.getEmail(), customer.getPhone(),
                billing == null ? null : billing.street(), billing == null ? null : billing.city(),
                billing == null ? null : billing.zipCode(), billing == null ? null : billing.country());
    }

    /** Four NULL columns = no billing address; a partly filled address is rejected. */
    protected static BillingAddress billingAddress(CustomerDto common) {
        String[] columns = {common.billingStreet(), common.billingCity(), common.billingZipCode(),
                common.billingCountry()};
        long filled = Arrays.stream(columns).filter(Objects::nonNull).count();
        if (filled == 0) {
            return null;
        }
        if (filled < columns.length) {
            throw new IllegalStateException("Zákazník " + common.customerId()
                    + " má fakturační adresu uloženou jen částečně.");
        }
        return new BillingAddress(common.billingStreet(), common.billingCity(), common.billingZipCode(),
                common.billingCountry());
    }
}
