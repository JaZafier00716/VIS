package cv04.tdg.dataaccess;

import dataaccess.h2.H2Database;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Table Data Gateway for cv04_customers: one stateless object for the whole table, DTOs in and out. */
public class CustomerTableGateway {
    private final H2Database database;

    public CustomerTableGateway(H2Database database) {
        this.database = database;
    }

    public void insert(CustomerDto customer) {
        database.run("CV04 TDG: vložení zákazníka", connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO cv04_customers (customer_id, name, email) VALUES (?, ?, ?)")) {
                statement.setString(1, customer.customerId());
                statement.setString(2, customer.name());
                statement.setString(3, customer.email());
                return statement.executeUpdate();
            }
        });
    }

    public void update(CustomerDto customer) {
        database.run("CV04 TDG: úprava zákazníka", connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "UPDATE cv04_customers SET name = ?, email = ? WHERE customer_id = ?")) {
                statement.setString(1, customer.name());
                statement.setString(2, customer.email());
                statement.setString(3, customer.customerId());
                return statement.executeUpdate();
            }
        });
    }

    public Optional<CustomerDto> findById(String customerId) {
        return database.run("CV04 TDG: načtení zákazníka", connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT customer_id, name, email FROM cv04_customers WHERE customer_id = ?")) {
                statement.setString(1, customerId);
                try (ResultSet rs = statement.executeQuery()) {
                    return rs.next() ? Optional.of(read(rs)) : Optional.empty();
                }
            }
        });
    }

    public List<CustomerDto> findAll() {
        return database.run("CV04 TDG: načtení zákazníků", connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT customer_id, name, email FROM cv04_customers ORDER BY customer_id");
                 ResultSet rs = statement.executeQuery()) {
                List<CustomerDto> customers = new ArrayList<>();
                while (rs.next()) {
                    customers.add(read(rs));
                }
                return customers;
            }
        });
    }

    private static CustomerDto read(ResultSet rs) throws SQLException {
        return new CustomerDto(rs.getString("customer_id"), rs.getString("name"), rs.getString("email"));
    }
}
