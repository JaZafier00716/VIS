package dataaccess.h2;

import dataaccess.dto.AddressDto;
import dataaccess.persistence.RecordIds;
import dataaccess.gateway.AddressGateway;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Table Data Gateway used by the Data Mapper (H2 table addresses).
 * Addresses refer to their customer through the customer_id foreign key (ON DELETE CASCADE).
 */
public class H2AddressGateway implements AddressGateway {
    private static final String ENTITY = "Adresa";
    private static final String COLUMNS = "address_id, customer_id, street, city, zip_code, country";
    private final H2Database database;

    public H2AddressGateway(H2Database database) {
        this.database = database;
    }

    @Override
    public void insert(AddressDto address) {
        String id = RecordIds.require(address.addressId(), ENTITY);
        database.run("Vložení adresy " + id, connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO addresses (" + COLUMNS + ") VALUES (?, ?, ?, ?, ?, ?)")) {
                statement.setString(1, id);
                statement.setString(2, address.customerId());
                statement.setString(3, address.street());
                statement.setString(4, address.city());
                statement.setString(5, address.zipCode());
                statement.setString(6, address.country());
                return statement.executeUpdate();
            }
        });
    }

    @Override
    public void update(AddressDto address) {
        String id = RecordIds.require(address.addressId(), ENTITY);
        int updated = database.run("Úprava adresy " + id, connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "UPDATE addresses SET customer_id = ?, street = ?, city = ?, zip_code = ?, country = ? "
                            + "WHERE address_id = ?")) {
                statement.setString(1, address.customerId());
                statement.setString(2, address.street());
                statement.setString(3, address.city());
                statement.setString(4, address.zipCode());
                statement.setString(5, address.country());
                statement.setString(6, id);
                return statement.executeUpdate();
            }
        });
        if (updated == 0) {
            throw RecordIds.missing(ENTITY, id);
        }
    }

    @Override
    public void delete(String id) {
        RecordIds.require(id, ENTITY);
        int deleted = database.run("Smazání adresy " + id,
                connection -> H2Records.deleteById(connection, "addresses", "address_id", id));
        if (deleted == 0) {
            throw RecordIds.missing(ENTITY, id);
        }
    }

    @Override
    public Optional<AddressDto> findById(String id) {
        return database.run("Načtení adresy " + id, connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT " + COLUMNS + " FROM addresses WHERE address_id = ?")) {
                statement.setString(1, id);
                try (ResultSet rs = statement.executeQuery()) {
                    return rs.next() ? Optional.of(map(rs)) : Optional.empty();
                }
            }
        });
    }

    @Override
    public List<AddressDto> findAll() {
        return database.run("Načtení adres", connection -> list(connection.prepareStatement(
                "SELECT " + COLUMNS + " FROM addresses ORDER BY seq")));
    }

    @Override
    public List<AddressDto> findByCustomerId(String customerId) {
        return database.run("Načtení adres zákazníka " + customerId, connection -> {
            PreparedStatement statement = connection.prepareStatement(
                    "SELECT " + COLUMNS + " FROM addresses WHERE customer_id = ? ORDER BY seq");
            statement.setString(1, customerId);
            return list(statement);
        });
    }

    private static List<AddressDto> list(PreparedStatement statement) throws SQLException {
        try (statement; ResultSet rs = statement.executeQuery()) {
            List<AddressDto> result = new ArrayList<>();
            while (rs.next()) {
                result.add(map(rs));
            }
            return result;
        }
    }

    private static AddressDto map(ResultSet rs) throws SQLException {
        return new AddressDto(rs.getString("address_id"), rs.getString("customer_id"), rs.getString("street"),
                rs.getString("city"), rs.getString("zip_code"), rs.getString("country"));
    }
}
