package dataaccess.mapper;

import business.domainmodel.Address;
import dataaccess.dto.AddressDto;

public final class AddressMapper {
    private AddressMapper() {}

    public static Address toDomain(AddressDto dto) {
        return Address.builder().addressId(dto.addressId()).customerId(dto.customerId()).street(dto.street())
                .city(dto.city()).zipCode(dto.zipCode()).country(dto.country()).build();
    }

    public static AddressDto toDto(Address address) {
        return new AddressDto(address.getAddressId(), address.getCustomerId(), address.getStreet(),
                address.getCity(), address.getZipCode(), address.getCountry());
    }
}
