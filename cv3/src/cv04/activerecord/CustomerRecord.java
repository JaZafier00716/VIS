package cv04.activerecord;

import dataaccess.h2.H2Database;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Optional;

/** Active Record: a customer that validates and saves itself (cv04 AR steps 1–2: Zakaznik.Save). */
public class CustomerRecord {
    private final H2Database database;
    private final String customerId;
    private String name;
    private String email;

    public CustomerRecord(H2Database database, String customerId, String name, String email) {
        this.database = database;
        this.customerId = customerId;
        this.name = name;
        this.email = email;
    }

    public static Optional<CustomerRecord> find(H2Database database, String customerId) {
        return database.run("CV04 AR: načtení zákazníka", connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT customer_id, name, email FROM cv04_customers WHERE customer_id = ?")) {
                statement.setString(1, customerId);
                try (ResultSet rs = statement.executeQuery()) {
                    return rs.next()
                            ? Optional.of(new CustomerRecord(database, rs.getString("customer_id"),
                                    rs.getString("name"), rs.getString("email")))
                            : Optional.empty();
                }
            }
        });
    }

    /** Domain rules live in the same class as the SQL - the defining trait of Active Record. */
    public void validate() {
        if (name == null || name.isBlank()) throw new IllegalArgumentException("Jméno je povinné.");
        if (email == null || !email.contains("@")) throw new IllegalArgumentException("E-mail není platný.");
    }

    public void changeEmail(String email) {
        this.email = email;
        validate();
    }

    /** Validate(), then INSERT or UPDATE by ID. */
    public void save() {
        validate();
        database.run("CV04 AR: uložení zákazníka", connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "MERGE INTO cv04_customers (customer_id, name, email) KEY (customer_id) VALUES (?, ?, ?)")) {
                statement.setString(1, customerId);
                statement.setString(2, name);
                statement.setString(3, email);
                return statement.executeUpdate();
            }
        });
    }

    public void delete() {
        database.run("CV04 AR: smazání zákazníka", connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "DELETE FROM cv04_customers WHERE customer_id = ?")) {
                statement.setString(1, customerId);
                return statement.executeUpdate();
            }
        });
    }

    public String getCustomerId() { return customerId; }
    public String getName() { return name; }
    public String getEmail() { return email; }
}
