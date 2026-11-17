package dataaccess.h2;

import dataaccess.dto.CustomerDto;
import dataaccess.gateway.CustomerGateway;
import dataaccess.persistence.RecordIds;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** CTI: Table Data Gateway used by the Data Mapper (H2 table customers, the common attributes). */
public class H2CustomerGateway implements CustomerGateway {
    private static final String ENTITY = "Zákazník";
    private static final String COLUMNS = "customer_id, customer_type, email, phone, billing_street, billing_city, "
            + "billing_zip_code, billing_country";
    private final H2Database database;

    public H2CustomerGateway(H2Database database) {
        this.database = database;
    }

    @Override
    public void insert(CustomerDto customer) {
        RecordIds.require(customer.customerId(), ENTITY);
        database.run("Vložení zákazníka " + customer.customerId(), connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO customers (" + COLUMNS + ") VALUES (?, ?, ?, ?, ?, ?, ?, ?)")) {
                statement.setString(1, customer.customerId());
                bindValues(statement, customer, 2);
                return statement.executeUpdate();
            }
        });
    }

    @Override
    public void update(CustomerDto customer) {
        String id = RecordIds.require(customer.customerId(), ENTITY);
        int updated = database.run("Úprava zákazníka " + id, connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "UPDATE customers SET customer_type = ?, email = ?, phone = ?, billing_street = ?, "
                            + "billing_city = ?, billing_zip_code = ?, billing_country = ? WHERE customer_id = ?")) {
                bindValues(statement, customer, 1);
                statement.setString(8, id);
                return statement.executeUpdate();
            }
        });
        if (updated == 0) {
            throw RecordIds.missing(ENTITY, id);
        }
    }

    /**
     * Orders block the delete (FK RESTRICT); the subtype row and the addresses are removed by ON DELETE CASCADE.
     */
    @Override
    public void delete(String id) {
        RecordIds.require(id, ENTITY);
        int deleted = database.run("Smazání zákazníka " + id, connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "DELETE FROM customers WHERE customer_id = ?")) {
                statement.setString(1, id);
                return statement.executeUpdate();
            }
        });
        if (deleted == 0) {
            throw RecordIds.missing(ENTITY, id);
        }
    }

    @Override
    public Optional<CustomerDto> findById(String id) {
        return database.run("Načtení zákazníka " + id, connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT " + COLUMNS + " FROM customers WHERE customer_id = ?")) {
                statement.setString(1, id);
                try (ResultSet rs = statement.executeQuery()) {
                    return rs.next() ? Optional.of(map(rs)) : Optional.empty();
                }
            }
        });
    }

    @Override
    public List<CustomerDto> findAll() {
        return database.run("Načtení zákazníků", connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT " + COLUMNS + " FROM customers ORDER BY customer_id");
                 ResultSet rs = statement.executeQuery()) {
                List<CustomerDto> result = new ArrayList<>();
                while (rs.next()) {
                    result.add(map(rs));
                }
                return result;
            }
        });
    }

    private static CustomerDto map(ResultSet rs) throws SQLException {
        return new CustomerDto(rs.getString("customer_id"), rs.getString("customer_type"), rs.getString("email"),
                rs.getString("phone"), rs.getString("billing_street"), rs.getString("billing_city"),
                rs.getString("billing_zip_code"), rs.getString("billing_country"));
    }

    /** Binds everything except the ID, starting at {@code index}. */
    private static void bindValues(PreparedStatement statement, CustomerDto customer, int index) throws SQLException {
        statement.setString(index++, customer.customerType());
        statement.setString(index++, customer.email());
        statement.setString(index++, customer.phone());
        statement.setString(index++, customer.billingStreet());
        statement.setString(index++, customer.billingCity());
        statement.setString(index++, customer.billingZipCode());
        statement.setString(index, customer.billingCountry());
    }
}
