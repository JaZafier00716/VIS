package dataaccess.h2;

import dataaccess.dto.PersonCustomerDto;
import dataaccess.gateway.PersonCustomerGateway;
import dataaccess.persistence.RecordIds;

import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Optional;

/** CTI: Table Data Gateway used by the Data Mapper (H2 table person_customers). */
public class H2PersonCustomerGateway implements PersonCustomerGateway {
    private static final String ENTITY = "Fyzická osoba";
    private final H2Database database;

    public H2PersonCustomerGateway(H2Database database) {
        this.database = database;
    }

    @Override
    public void insert(PersonCustomerDto person) {
        RecordIds.require(person.customerId(), ENTITY);
        database.run("Vložení osoby " + person.customerId(), connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO person_customers (customer_id, first_name, last_name, birth_date) "
                            + "VALUES (?, ?, ?, ?)")) {
                statement.setString(1, person.customerId());
                statement.setString(2, person.firstName());
                statement.setString(3, person.lastName());
                statement.setDate(4, person.birthDate() == null ? null : Date.valueOf(person.birthDate()));
                return statement.executeUpdate();
            }
        });
    }

    @Override
    public void update(PersonCustomerDto person) {
        String id = RecordIds.require(person.customerId(), ENTITY);
        int updated = database.run("Úprava osoby " + id, connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "UPDATE person_customers SET first_name = ?, last_name = ?, birth_date = ? "
                            + "WHERE customer_id = ?")) {
                statement.setString(1, person.firstName());
                statement.setString(2, person.lastName());
                statement.setDate(3, person.birthDate() == null ? null : Date.valueOf(person.birthDate()));
                statement.setString(4, id);
                return statement.executeUpdate();
            }
        });
        if (updated == 0) {
            throw RecordIds.missing(ENTITY, id);
        }
    }

    @Override
    public void delete(String id) {
        database.run("Smazání osoby " + id, connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "DELETE FROM person_customers WHERE customer_id = ?")) {
                statement.setString(1, id);
                return statement.executeUpdate();
            }
        });
    }

    @Override
    public Optional<PersonCustomerDto> findById(String id) {
        return database.run("Načtení osoby " + id, connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT customer_id, first_name, last_name, birth_date FROM person_customers "
                            + "WHERE customer_id = ?")) {
                statement.setString(1, id);
                try (ResultSet rs = statement.executeQuery()) {
                    if (!rs.next()) {
                        return Optional.empty();
                    }
                    Date birthDate = rs.getDate("birth_date");
                    return Optional.of(new PersonCustomerDto(rs.getString("customer_id"), rs.getString("first_name"),
                            rs.getString("last_name"), birthDate == null ? null : birthDate.toLocalDate()));
                }
            }
        });
    }
}
