package business.domainmodel;

import business.observer.IOrderObserver;
import lombok.AccessLevel;
import lombok.Getter;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;

@Getter
public class Order extends BaseEntity {
    private final String customerId;
    private final String materialId;
    private final OrderSpecification specification;
    private OrderStatus status;
    private transient Supplier<Customer> customerLoader;
    private transient Customer customer;
    private transient boolean customerLoaded = false;
    // Dependent Mapping: the order owns its items and their lifecycle.
    private final List<OrderItem> items = new ArrayList<>();
    /**
     * CV09 Singleton: the order number from {@code OrderNumberGenerator}. It is held in memory only (not
     * persisted), so an order loaded from storage has none.
     */
    private Integer number;
    // CV09 Observer: the order is the Subject. Observers and pending changes are runtime state, never stored.
    @Getter(AccessLevel.NONE)
    private final transient List<IOrderObserver> observers = new ArrayList<>();
    @Getter(AccessLevel.NONE)
    private final transient List<StatusChange> pendingStatusChanges = new ArrayList<>();

    /** A status change that has happened but has not been announced to the observers yet. */
    private record StatusChange(OrderStatus oldStatus, OrderStatus newStatus) {}

    public Order(String orderId, String customerId, String materialId,
                 OrderSpecification specification, Instant createdAt) {
        super(orderId, createdAt);
        this.customerId = customerId;
        this.materialId = materialId;
        this.specification = specification;
        this.status = OrderStatus.CEKA_NA_POTVRZENI;
    }

    public String getOrderId() {
        return getId();
    }

    /** CV09: assigns the order number once; a number is never replaced. */
    public void assignNumber(int number) {
        if (this.number != null) {
            throw new IllegalStateException("Objednávka už má číslo " + this.number + ".");
        }
        this.number = number;
    }

    /** The order number if it has one, otherwise the technical ID – for messages. */
    public String getReference() {
        return number != null ? number.toString() : getId();
    }

    public Customer getCustomer() {
        if (customerLoader != null && !customerLoaded) {
            customer = customerLoader.get();
            customerLoaded = true;
        }
        return customer;
    }

    public void setCustomer(Customer customer) {
        this.customer = customer;
        this.customerLoaded = true;
    }

    public void setCustomerLoader(Supplier<Customer> customerLoader) {
        this.customerLoader = customerLoader;
    }

    public List<OrderItem> getItems() {
        return Collections.unmodifiableList(items);
    }

    public OrderItem addItem(String materialId, int quantity, Money unitPrice,
                             ProductConfiguration configuration) {
        int nextLine = items.stream().mapToInt(OrderItem::getLineNumber).max().orElse(0) + 1;
        OrderItem item = new OrderItem(nextLine, materialId, quantity, unitPrice, configuration);
        items.add(item);
        markUpdated();
        return item;
    }

    /** The stored values of one item, for {@link #restore}. */
    public record StoredItem(int lineNumber, String materialId, int quantity, Money unitPrice,
                             ProductConfiguration configuration) {}

    /**
     * Reconstitutes an order exactly as it was stored: for loading from storage, not for business use.
     * The status and the item line numbers are taken as they are; no business transition ({@link #confirm()},
     * {@link #addItem}) runs.
     */
    public static Order restore(String orderId, String customerId, String materialId,
                                OrderSpecification specification, Instant createdAt, OrderStatus status,
                                List<StoredItem> storedItems) {
        Order order = new Order(orderId, customerId, materialId, specification, createdAt);
        order.status = status;
        for (StoredItem item : storedItems) {
            if (order.items.stream().anyMatch(existing -> existing.getLineNumber() == item.lineNumber())) {
                throw new IllegalArgumentException("Položka " + item.lineNumber() + " už v objednávce existuje.");
            }
            order.items.add(new OrderItem(item.lineNumber(), item.materialId(), item.quantity(), item.unitPrice(),
                    item.configuration()));
        }
        return order;
    }

    public void changeItemQuantity(int lineNumber, int quantity) {
        findItem(lineNumber).changeQuantity(quantity);
        markUpdated();
    }

    public void removeItem(int lineNumber) {
        items.remove(findItem(lineNumber));
        markUpdated();
    }

    private OrderItem findItem(int lineNumber) {
        return items.stream()
                .filter(item -> item.getLineNumber() == lineNumber)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Položka " + lineNumber + " v objednávce neexistuje."));
    }

    public void validate() {
        specification.validate();
        if (customerId == null || customerId.isBlank()) {
            throw new IllegalArgumentException("Objednávka musí mít zákazníka.");
        }
        if (materialId == null || materialId.isBlank()) {
            throw new IllegalArgumentException("Objednávka musí mít materiál.");
        }
    }

    public void confirm() {
        changeStatus(OrderStatus.CEKA_NA_POTVRZENI, OrderStatus.POTVRZENA);
    }

    public void startProduction() {
        changeStatus(OrderStatus.POTVRZENA, OrderStatus.VE_VYROBE);
    }

    public void prepareForExpedition() {
        changeStatus(OrderStatus.VE_VYROBE, OrderStatus.PRIPRAVENA_K_EXPEDICI);
    }

    public void ship() {
        changeStatus(OrderStatus.PRIPRAVENA_K_EXPEDICI, OrderStatus.EXPEDOVANA);
    }

    public void close() {
        changeStatus(OrderStatus.EXPEDOVANA, OrderStatus.UZAVRENA);
    }

    /**
     * The one place where the status changes. An invalid transition throws and changes nothing (and so is never
     * announced). A valid one is only recorded as pending: the observers learn about it from
     * {@link #publishStatusChanges()}, which the service calls after the change was saved.
     */
    private void changeStatus(OrderStatus expected, OrderStatus target) {
        if (status != expected || !status.canChangeTo(target)) {
            throw new IllegalStateException("Objednávku " + getReference() + " nelze převést ze stavu „"
                    + status.getLabel() + "“ do stavu „" + target.getLabel() + "“.");
        }
        OrderStatus oldStatus = status;
        status = target;
        pendingStatusChanges.add(new StatusChange(oldStatus, target));
        markUpdated();
    }

    public void registerObserver(IOrderObserver observer) {
        if (observer != null && !observers.contains(observer)) {
            observers.add(observer);
        }
    }

    public void unregisterObserver(IOrderObserver observer) {
        observers.remove(observer);
    }

    /** Notifies the registered observers of every pending status change, then forgets those changes. */
    public void publishStatusChanges() {
        List<StatusChange> changes = List.copyOf(pendingStatusChanges);
        pendingStatusChanges.clear();
        for (StatusChange change : changes) {
            for (IOrderObserver observer : List.copyOf(observers)) {
                observer.statusChanged(this, change.oldStatus(), change.newStatus());
            }
        }
    }

    /** Forgets pending status changes without announcing them (the change was not saved). */
    public void discardStatusChanges() {
        pendingStatusChanges.clear();
    }
}
