package business.service;

import business.domainmodel.Money;
import business.domainmodel.Order;
import business.domainmodel.OrderItem;
import business.domainmodel.OrderSpecification;
import business.domainmodel.ProductConfiguration;
import business.observer.IOrderObserver;
import business.repository.OrderRepository;
import business.repository.UnitOfWorkControl;
import business.service.numbering.OrderNumberGenerator;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class OrderService {
    private final OrderRepository orders;
    private final CustomerService customers;
    private final MaterialService materials;
    private final UnitOfWorkControl unitOfWork;
    /** CV09 Observer: who wants to hear about saved status changes; none by default. */
    private final List<IOrderObserver> observers = new ArrayList<>();

    public OrderService(OrderRepository orders, CustomerService customers, MaterialService materials,
                        UnitOfWorkControl unitOfWork) {
        this.orders = orders;
        this.customers = customers;
        this.materials = materials;
        this.unitOfWork = unitOfWork;
    }

    public Order createOrder(String id, String customerId, String materialId, OrderSpecification specification) {
        if (specification == null) throw new IllegalArgumentException("Objednávka musí mít specifikaci.");
        customers.findCustomer(customerId);
        materials.findMaterial(materialId);
        Order order = newOrder(id, customerId, materialId, specification);
        orders.add(order);
        commit();
        return order;
    }

    /** Validates the input and builds a new order; registers and commits nothing. */
    Order newOrder(String id, String customerId, String materialId, OrderSpecification specification) {
        if (specification == null) throw new IllegalArgumentException("Objednávka musí mít specifikaci.");
        String orderId = id == null || id.isBlank() ? orders.nextId() : id;
        Order order = new Order(orderId, customerId, materialId, specification, Instant.now());
        order.validate();
        // CV09 Singleton: every new order gets the next number from the one generator of this run.
        order.assignNumber(OrderNumberGenerator.getInstance().getNextNumber());
        return order;
    }

    /** Foreign Key Mapping + Lazy Load: the customer is resolved by the data layer on first access. */
    public Order findOrder(String id) {
        return orders.findById(id).orElseThrow(() -> new IllegalArgumentException("Objednávka " + id + " neexistuje."));
    }

    public List<Order> findAll() {
        return orders.findAll();
    }

    public OrderItem addItem(String orderId, String materialId, int quantity, BigDecimal unitPrice,
                             ProductConfiguration configuration) {
        Order order = findOrder(orderId);
        materials.findMaterial(materialId);
        OrderItem item = order.addItem(materialId, quantity, Money.czk(unitPrice), configuration);
        orders.update(order);
        commit();
        return item;
    }

    public void changeItemQuantity(String orderId, int lineNumber, int quantity) {
        Order order = findOrder(orderId);
        order.changeItemQuantity(lineNumber, quantity);
        orders.update(order);
        commit();
    }

    public void removeItem(String orderId, int lineNumber) {
        Order order = findOrder(orderId);
        order.removeItem(lineNumber);
        orders.update(order);
        commit();
    }

    public void registerObserver(IOrderObserver observer) {
        if (observer != null && !observers.contains(observer)) {
            observers.add(observer);
        }
    }

    public void unregisterObserver(IOrderObserver observer) {
        observers.remove(observer);
    }

    public void confirmOrder(String orderId) {
        changeStatus(orderId, Order::confirm);
    }

    public void startProduction(String orderId) {
        changeStatus(orderId, Order::startProduction);
    }

    public void prepareForExpedition(String orderId) {
        changeStatus(orderId, Order::prepareForExpedition);
    }

    public void shipOrder(String orderId) {
        changeStatus(orderId, Order::ship);
    }

    public void closeOrder(String orderId) {
        changeStatus(orderId, Order::close);
    }

    /**
     * CV09 Observer: one status change as one business operation. An invalid transition throws before anything
     * is registered, so nobody is notified. The observers are notified only after a successful commit: if they
     * were told earlier and the save then failed, production or expedition would act on a change that does not
     * exist in the storage.
     */
    private void changeStatus(String orderId, Consumer<Order> transition) {
        Order order = findOrder(orderId);
        transition.accept(order);
        orders.update(order);
        try {
            commit();
        } catch (RuntimeException e) {
            order.discardStatusChanges();
            throw e;
        }
        // The service's observers are attached only for this announcement, so an unregistered observer is
        // never reached through an order that stays cached in the Identity Map.
        observers.forEach(order::registerObserver);
        try {
            order.publishStatusChanges();
        } finally {
            observers.forEach(order::unregisterObserver);
        }
    }

    /** The order's items are removed with it (Dependent Mapping). */
    public void deleteOrder(String orderId) {
        orders.remove(findOrder(orderId));
        commit();
    }

    private void commit() {
        try {
            unitOfWork.commit();
        } catch (RuntimeException e) {
            unitOfWork.rollback();
            throw e;
        }
    }
}
