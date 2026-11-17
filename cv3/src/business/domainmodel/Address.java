package business.domainmodel;

import lombok.Builder;
import lombok.Getter;

/**
 * A customer's address. Has its own Identity Field and refers to its customer by ID (Foreign Key Mapping).
 */
@Getter
public class Address extends BaseEntity {
    private final String customerId;
    private final String street;
    private final String city;
    private final String zipCode;
    private final String country;

    @Builder
    private Address(String addressId, String customerId, String street, String city, String zipCode, String country) {
        super(addressId);
        this.customerId = customerId;
        this.street = street;
        this.city = city;
        this.zipCode = zipCode;
        this.country = country;
    }

    public String getAddressId() {
        return getId();
    }

    public void validate() {
        if (customerId == null || customerId.isBlank()) {
            throw new IllegalArgumentException("Adresa musí patřit zákazníkovi.");
        }
        if (street == null || street.isBlank() || city == null || city.isBlank()) {
            throw new IllegalArgumentException("Adresa musí mít ulici a město.");
        }
    }
}
