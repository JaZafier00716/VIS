package cz.vsb.catering.business.service;

import cz.vsb.catering.business.domain.Customer;
import cz.vsb.catering.business.domain.ValidationException;
import cz.vsb.catering.business.port.CustomerMapper;
import cz.vsb.catering.business.port.TransactionRunner;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;

/** Facade for UC05 Spravovat zákazníky «CRUD». */
public class CustomerService {
    private final CustomerMapper customers;
    private final TransactionRunner tx;

    public CustomerService(CustomerMapper customers, TransactionRunner tx) {
        this.customers = customers;
        this.tx = tx;
    }

    public List<Customer> findAll() { return tx.inTransaction(customers::findAll); }

    /** Customers whose name, company, e-mail, phone or city contains the text (case-insensitive). */
    public List<Customer> search(String text) {
        String t = text == null ? "" : text.trim().toLowerCase(Locale.ROOT);
        return findAll().stream().filter(c -> Stream.of(c.getName(), c.getCompanyName(), c.getEmail(), c.getPhone(), c.getCity())
                .anyMatch(v -> v != null && v.toLowerCase(Locale.ROOT).contains(t))).toList();
    }

    public Customer create(String name, String companyName, String email, String phone, String city) {
        Customer customer = Customer.create(name, companyName, email, phone, city);
        return tx.inTransaction(() -> {
            requireUniqueEmail(customer);
            customers.insert(customer);
            return customer;
        });
    }

    public Customer update(long customerId, String name, String companyName, String email, String phone, String city) {
        return tx.inTransaction(() -> {
            Customer customer = customers.findById(customerId)
                    .orElseThrow(() -> new ValidationException("Zákazník neexistuje."));
            customer.update(name, companyName, email, phone, city);
            requireUniqueEmail(customer);
            customers.update(customer);
            return customer;
        });
    }

    /** A customer with projects cannot be deleted (projects keep their customer). */
    public void delete(long customerId) {
        tx.inTransaction(() -> {
            int projects = customers.countProjects(customerId);
            if (projects > 0)
                throw new ValidationException("Zákazníka nelze smazat, protože má projekty (" + projects + ").");
            customers.delete(customerId);
        });
    }

    /** Checked before the INSERT/UPDATE so the form gets a field error instead of uq_customers_email. */
    private void requireUniqueEmail(Customer customer) {
        // shortcut: scans all customers, fine for hundreds; add findByEmail to the mapper when it grows
        boolean taken = customers.findAll().stream().anyMatch(c ->
                customer.getEmail().equalsIgnoreCase(c.getEmail()) && !c.getId().equals(customer.getId()));
        if (taken) {
            String msg = "Zákazník s e-mailem „" + customer.getEmail() + "“ již existuje.";
            throw new ValidationException(msg, Map.of("email", msg));
        }
    }
}
