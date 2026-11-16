package cz.vsb.catering.data.oracle;

import cz.vsb.catering.business.domain.Customer;
import cz.vsb.catering.business.port.CustomerMapper;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

/** Data Mapper for customers. */
public class OracleCustomerMapper implements CustomerMapper {
    private static final String SELECT =
            "SELECT customer_id, name, company_name, email, phone, city FROM customers ";

    @Override
    public List<Customer> findAll() {
        return Jdbc.query(SELECT + "ORDER BY name, customer_id", OracleCustomerMapper::read);
    }

    @Override
    public Optional<Customer> findById(long customerId) {
        return Jdbc.queryOne(SELECT + "WHERE customer_id = ?", OracleCustomerMapper::read, customerId);
    }

    @Override
    public void insert(Customer c) {
        c.setId(Jdbc.insert("INSERT INTO customers (name, company_name, email, phone, city) VALUES (?, ?, ?, ?, ?)",
                "customer_id", c.getName(), c.getCompanyName(), c.getEmail(), c.getPhone(), c.getCity()));
    }

    @Override
    public void update(Customer c) {
        Jdbc.update("UPDATE customers SET name = ?, company_name = ?, email = ?, phone = ?, city = ? WHERE customer_id = ?",
                c.getName(), c.getCompanyName(), c.getEmail(), c.getPhone(), c.getCity(), c.getId());
    }

    @Override
    public void delete(long customerId) {
        Jdbc.update("DELETE FROM customers WHERE customer_id = ?", customerId);
    }

    @Override
    public int countProjects(long customerId) {
        return Jdbc.queryOne("SELECT COUNT(*) FROM projects WHERE customer_id = ?", rs -> rs.getInt(1), customerId).orElse(0);
    }

    /** Also used by OracleProjectMapper (projects are read with their customer). */
    static Customer read(ResultSet rs) throws SQLException {
        Customer c = new Customer(rs.getString("name"), rs.getString("company_name"), rs.getString("email"),
                rs.getString("phone"), rs.getString("city"));
        c.setId(rs.getLong("customer_id"));
        return c;
    }
}
