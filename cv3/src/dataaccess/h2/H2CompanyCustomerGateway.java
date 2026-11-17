package dataaccess.h2;

import dataaccess.dto.CompanyCustomerDto;
import dataaccess.gateway.CompanyCustomerGateway;
import dataaccess.persistence.RecordIds;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Optional;

/** CTI: Table Data Gateway used by the Data Mapper (H2 table company_customers). */
public class H2CompanyCustomerGateway implements CompanyCustomerGateway {
    private static final String ENTITY = "Firma";
    private final H2Database database;

    public H2CompanyCustomerGateway(H2Database database) {
        this.database = database;
    }

    @Override
    public void insert(CompanyCustomerDto company) {
        RecordIds.require(company.customerId(), ENTITY);
        database.run("Vložení firmy " + company.customerId(), connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO company_customers (customer_id, company_name, ico, dic, contact_person) "
                            + "VALUES (?, ?, ?, ?, ?)")) {
                statement.setString(1, company.customerId());
                statement.setString(2, company.companyName());
                statement.setString(3, company.ico());
                statement.setString(4, company.dic());
                statement.setString(5, company.contactPerson());
                return statement.executeUpdate();
            }
        });
    }

    @Override
    public void update(CompanyCustomerDto company) {
        String id = RecordIds.require(company.customerId(), ENTITY);
        int updated = database.run("Úprava firmy " + id, connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "UPDATE company_customers SET company_name = ?, ico = ?, dic = ?, contact_person = ? "
                            + "WHERE customer_id = ?")) {
                statement.setString(1, company.companyName());
                statement.setString(2, company.ico());
                statement.setString(3, company.dic());
                statement.setString(4, company.contactPerson());
                statement.setString(5, id);
                return statement.executeUpdate();
            }
        });
        if (updated == 0) {
            throw RecordIds.missing(ENTITY, id);
        }
    }

    @Override
    public void delete(String id) {
        database.run("Smazání firmy " + id, connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "DELETE FROM company_customers WHERE customer_id = ?")) {
                statement.setString(1, id);
                return statement.executeUpdate();
            }
        });
    }

    @Override
    public Optional<CompanyCustomerDto> findById(String id) {
        return database.run("Načtení firmy " + id, connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT customer_id, company_name, ico, dic, contact_person FROM company_customers "
                            + "WHERE customer_id = ?")) {
                statement.setString(1, id);
                try (ResultSet rs = statement.executeQuery()) {
                    return rs.next()
                            ? Optional.of(new CompanyCustomerDto(rs.getString("customer_id"),
                                    rs.getString("company_name"), rs.getString("ico"), rs.getString("dic"),
                                    rs.getString("contact_person")))
                            : Optional.empty();
                }
            }
        });
    }
}
