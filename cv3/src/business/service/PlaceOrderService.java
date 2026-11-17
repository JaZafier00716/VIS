package business.service;

import business.domainmodel.Address;
import business.domainmodel.BillingAddress;
import business.domainmodel.Customer;
import business.domainmodel.Order;
import business.domainmodel.OrderSpecification;
import business.repository.Repositories;
import business.repository.UnitOfWorkControl;

import java.time.LocalDate;

/**
 * CV05 scenario A: the customer (if new), the address and the order are created in one business operation
 * and saved together by one Unit of Work commit. The business layer decides when the operation starts and
 * ends; the Unit of Work (data access layer) records the changes and writes them in one transaction.
 */
public class PlaceOrderService {
    /**
     * Who orders (cv07 p.15, scenario A step 1): an existing customer by ID, or a new person or company chosen by
     * the caller. A new customer always gets a generated ID.
     */
    public sealed interface CustomerData {
        record Existing(String customerId) implements CustomerData {}

        record NewPerson(String email, String phone, BillingAddress billingAddress, String firstName,
                         String lastName, LocalDate birthDate) implements CustomerData {}

        record NewCompany(String email, String phone, BillingAddress billingAddress, String companyName, String ico,
                          String dic, String contactPerson) implements CustomerData {}

        static CustomerData existing(String customerId) {
            return new Existing(customerId);
        }
    }

    public record AddressData(String street, String city, String zipCode, String country) {
    }

    private final Repositories repositories;
    private final CustomerService customers;
    private final MaterialService materials;
    private final OrderService orders;
    private final UnitOfWorkControl unitOfWork;

    public PlaceOrderService(Repositories repositories, CustomerService customers, MaterialService materials,
                             OrderService orders, UnitOfWorkControl unitOfWork) {
        this.repositories = repositories;
        this.customers = customers;
        this.materials = materials;
        this.orders = orders;
        this.unitOfWork = unitOfWork;
    }

    /**
     * Validates every input first and only then registers the new objects, so a rejected request leaves
     * the Unit of Work without changes. One {@code commit()} saves everything; a failed commit rolls back.
     *
     * @return the new order in state {@code CEKA_NA_POTVRZENI}, linked to its customer, whose addresses
     *         contain the new address
     */
    public Order placeOrder(CustomerData customerData, AddressData addressData, String materialId,
                            OrderSpecification specification) {
        // 1. Validate: customer, address, material, specification. Nothing is registered yet, so a rejected
        //    request needs no rollback and leaves the Unit of Work unchanged.
        if (customerData == null) throw new IllegalArgumentException("Chybí údaje zákazníka.");
        if (addressData == null) throw new IllegalArgumentException("Chybí adresa.");
        boolean newCustomer = !(customerData instanceof CustomerData.Existing);
        // An existing customer must be found; a new one always gets a generated ID.
        Customer customer = switch (customerData) {
            case CustomerData.Existing existing -> customers.findCustomer(existing.customerId());
            case CustomerData.NewPerson p -> customers.newPersonCustomer(null, p.email(), p.phone(),
                    p.billingAddress(), p.firstName(), p.lastName(), p.birthDate());
            case CustomerData.NewCompany c -> customers.newCompanyCustomer(null, c.email(), c.phone(),
                    c.billingAddress(), c.companyName(), c.ico(), c.dic(), c.contactPerson());
        };
        Address address = customers.newAddress(customer.getCustomerId(), addressData.street(),
                addressData.city(), addressData.zipCode(), addressData.country());
        materials.findMaterial(materialId);
        Order order = orders.newOrder(null, customer.getCustomerId(), materialId, specification);

        try {
            // 2. Register the new objects in the Unit of Work (it orders the inserts: customer, address, order).
            if (newCustomer) {
                repositories.customers().add(customer);
            }
            repositories.addresses().add(address);
            repositories.orders().add(order);
            customer.addAddress(address);
            order.setCustomer(customer);

            // 3. One commit for the whole business operation; on failure discard all registered changes.
            unitOfWork.commit();
            return order;
        } catch (RuntimeException e) {
            unitOfWork.rollback();
            throw e;
        }
    }
}
