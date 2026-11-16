package cz.vsb.catering.business.fake;

import cz.vsb.catering.business.domain.Customer;
import cz.vsb.catering.business.port.CustomerMapper;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Stores copies and hands out copies, like a real database. */
public class FakeCustomerMapper implements CustomerMapper {
    public final List<Customer> customers = new ArrayList<>();
    public final Map<Long, Integer> projectCounts = new HashMap<>();
    private long nextId = 100;

    public Customer add(long id, String name) {
        return add(id, name, null);
    }

    public Customer add(long id, String name, String email) {
        Customer c = new Customer(name, null, email, null, null);
        c.setId(id);
        customers.add(c);
        return c;
    }

    @Override public List<Customer> findAll() { return customers.stream().map(FakeCustomerMapper::copy).toList(); }

    @Override public Optional<Customer> findById(long customerId) {
        return customers.stream().filter(c -> c.getId() == customerId).findFirst().map(FakeCustomerMapper::copy);
    }

    @Override public void insert(Customer customer) {
        customer.setId(nextId++);
        customers.add(copy(customer));
    }

    @Override public void update(Customer customer) {
        customers.replaceAll(c -> c.getId().equals(customer.getId()) ? copy(customer) : c);
    }

    @Override public void delete(long customerId) { customers.removeIf(c -> c.getId() == customerId); }

    @Override public int countProjects(long customerId) { return projectCounts.getOrDefault(customerId, 0); }

    public Customer stored(long id) { return customers.stream().filter(c -> c.getId() == id).findFirst().orElse(null); }

    private static Customer copy(Customer c) {
        Customer copy = new Customer(c.getName(), c.getCompanyName(), c.getEmail(), c.getPhone(), c.getCity());
        copy.setId(c.getId());
        return copy;
    }
}
