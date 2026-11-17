package dataaccess.row;

public record AddressRow(String addressId, String customerId, String street, String city,
                         String zipCode, String country) {}
