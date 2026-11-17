package dataaccess.mapper;

import dataaccess.dto.AddressDto;
import dataaccess.row.AddressRow;

public final class AddressRowMapper {
    private AddressRowMapper() {}

    public static AddressRow toRow(AddressDto dto) {
        return new AddressRow(dto.addressId(), dto.customerId(), dto.street(), dto.city(), dto.zipCode(),
                dto.country());
    }

    public static AddressDto toDto(AddressRow row) {
        return new AddressDto(row.addressId(), row.customerId(), row.street(), row.city(), row.zipCode(),
                row.country());
    }
}
