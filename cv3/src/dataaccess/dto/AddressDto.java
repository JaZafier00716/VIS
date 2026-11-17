package dataaccess.dto;

public record AddressDto(String addressId, String customerId, String street, String city,
                         String zipCode, String country) {}
