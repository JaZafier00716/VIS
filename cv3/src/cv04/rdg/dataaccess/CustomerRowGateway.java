package cv04.rdg.dataaccess;

import dataaccess.h2.H2Database;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Row Data Gateway: one object = one cv04_customers row, with its own SQL and no domain logic. */
public class CustomerRowGateway {
    private final H2Database database;
    private final String customerId;
    private String name;
    private String email;

    public CustomerRowGateway(H2Database database, String customerId, String name, String email) {
        this.database = database;
        this.customerId = customerId;
        this.name = name;
        this.email = email;
    }

    public static Optional<CustomerRowGateway> find(H2Database database, String customerId) {
        return database.run("CV04 RDG: načtení zákazníka", connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT customer_id, name, email FROM cv04_customers WHERE customer_id = ?")) {
                statement.setString(1, customerId);
                try (ResultSet rs = statement.executeQuery()) {
                    return rs.next() ? Optional.of(read(database, rs)) : Optional.empty();
                }
            }
        });
    }

    public static List<CustomerRowGateway> findAll(H2Database database) {
        return database.run("CV04 RDG: načtení zákazníků", connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT customer_id, name, email FROM cv04_customers ORDER BY customer_id");
                 ResultSet rs = statement.executeQuery()) {
                List<CustomerRowGateway> rows = new ArrayList<>();
                while (rs.next()) {
                    rows.add(read(database, rs));
                }
                return rows;
            }
        });
    }

    public void insert() {
        database.run("CV04 RDG: vložení zákazníka", connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO cv04_customers (customer_id, name, email) VALUES (?, ?, ?)")) {
                statement.setString(1, customerId);
                statement.setString(2, name);
                statement.setString(3, email);
                return statement.executeUpdate();
            }
        });
    }

    public void update() {
        database.run("CV04 RDG: úprava zákazníka", connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "UPDATE cv04_customers SET name = ?, email = ? WHERE customer_id = ?")) {
                statement.setString(1, name);
                statement.setString(2, email);
                statement.setString(3, customerId);
                return statement.executeUpdate();
            }
        });
    }

    public void delete() {
        database.run("CV04 RDG: smazání zákazníka", connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "DELETE FROM cv04_customers WHERE customer_id = ?")) {
                statement.setString(1, customerId);
                return statement.executeUpdate();
            }
        });
    }

    private static CustomerRowGateway read(H2Database database, ResultSet rs) throws SQLException {
        return new CustomerRowGateway(database, rs.getString("customer_id"), rs.getString("name"),
                rs.getString("email"));
    }

    public String getCustomerId() { return customerId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
}
