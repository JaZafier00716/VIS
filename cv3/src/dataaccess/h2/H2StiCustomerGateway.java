package dataaccess.h2;

import dataaccess.dto.StiCustomerDto;
import dataaccess.persistence.RecordIds;
import dataaccess.gateway.StiCustomerGateway;

import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * STI: Table Data Gateway for {@code customers_sti}, the single table of the whole Customer hierarchy
 * (cv07 p.5–6). Rows with the wrong columns for their {@code customer_type} are rejected by the CHECK constraint.
 */
public class H2StiCustomerGateway implements StiCustomerGateway {
    private static final String ENTITY = "Zákazník";
    private static final String COLUMNS = "customer_id, customer_type, email, phone, billing_street, billing_city, "
            + "billing_zip_code, billing_country, first_name, last_name, birth_date, company_name, ico, dic, "
            + "contact_person";
    private final H2Database database;

    public H2StiCustomerGateway(H2Database database) {
        this.database = database;
    }

    @Override
    public void insert(StiCustomerDto customer) {
        RecordIds.require(customer.customerId(), ENTITY);
        database.run("Vložení zákazníka " + customer.customerId(), connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO customers_sti (" + COLUMNS + ") "
                            + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
                bind(statement, customer);
                return statement.executeUpdate();
            }
        });
    }

    @Override
    public void update(StiCustomerDto customer) {
        String id = RecordIds.require(customer.customerId(), ENTITY);
        int updated = database.run("Úprava zákazníka " + id, connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "UPDATE customers_sti SET customer_type = ?, email = ?, phone = ?, billing_street = ?, "
                            + "billing_city = ?, billing_zip_code = ?, billing_country = ?, first_name = ?, "
                            + "last_name = ?, birth_date = ?, company_name = ?, ico = ?, dic = ?, "
                            + "contact_person = ? WHERE customer_id = ?")) {
                bindValues(statement, customer, 1);
                statement.setString(15, id);
                return statement.executeUpdate();
            }
        });
        if (updated == 0) {
            throw RecordIds.missing(ENTITY, id);
        }
    }

    /** Orders block the delete (FK RESTRICT); the customer's addresses are removed by ON DELETE CASCADE. */
    @Override
    public void delete(String id) {
        RecordIds.require(id, ENTITY);
        int deleted = database.run("Smazání zákazníka " + id, connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "DELETE FROM customers_sti WHERE customer_id = ?")) {
                statement.setString(1, id);
                return statement.executeUpdate();
            }
        });
        if (deleted == 0) {
            throw RecordIds.missing(ENTITY, id);
        }
    }

    @Override
    public Optional<StiCustomerDto> findById(String id) {
        return database.run("Načtení zákazníka " + id, connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT " + COLUMNS + " FROM customers_sti WHERE customer_id = ?")) {
                statement.setString(1, id);
                try (ResultSet rs = statement.executeQuery()) {
                    return rs.next() ? Optional.of(map(rs)) : Optional.empty();
                }
            }
        });
    }

    @Override
    public List<StiCustomerDto> findAll() {
        return database.run("Načtení zákazníků", connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT " + COLUMNS + " FROM customers_sti ORDER BY customer_id");
                 ResultSet rs = statement.executeQuery()) {
                List<StiCustomerDto> result = new ArrayList<>();
                while (rs.next()) {
                    result.add(map(rs));
                }
                return result;
            }
        });
    }

    private static StiCustomerDto map(ResultSet rs) throws SQLException {
        Date birthDate = rs.getDate("birth_date");
        return new StiCustomerDto(rs.getString("customer_id"), rs.getString("customer_type"), rs.getString("email"),
                rs.getString("phone"), rs.getString("billing_street"), rs.getString("billing_city"),
                rs.getString("billing_zip_code"), rs.getString("billing_country"), rs.getString("first_name"),
                rs.getString("last_name"), birthDate == null ? null : birthDate.toLocalDate(),
                rs.getString("company_name"), rs.getString("ico"), rs.getString("dic"),
                rs.getString("contact_person"));
    }

    private static void bind(PreparedStatement statement, StiCustomerDto customer) throws SQLException {
        statement.setString(1, customer.customerId());
        bindValues(statement, customer, 2);
    }

    /** Binds everything except the ID, starting at {@code index}. */
    private static void bindValues(PreparedStatement statement, StiCustomerDto customer, int index)
            throws SQLException {
        statement.setString(index++, customer.customerType());
        statement.setString(index++, customer.email());
        statement.setString(index++, customer.phone());
        statement.setString(index++, customer.billingStreet());
        statement.setString(index++, customer.billingCity());
        statement.setString(index++, customer.billingZipCode());
        statement.setString(index++, customer.billingCountry());
        statement.setString(index++, customer.firstName());
        statement.setString(index++, customer.lastName());
        statement.setDate(index++, customer.birthDate() == null ? null : Date.valueOf(customer.birthDate()));
        statement.setString(index++, customer.companyName());
        statement.setString(index++, customer.ico());
        statement.setString(index++, customer.dic());
        statement.setString(index, customer.contactPerson());
    }
}
