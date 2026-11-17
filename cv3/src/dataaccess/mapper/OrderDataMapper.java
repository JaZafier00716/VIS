package dataaccess.mapper;

import business.domainmodel.Order;
import business.repository.OrderRepository;
import dataaccess.dto.OrderDto;
import dataaccess.gateway.OrderGateway;
import dataaccess.identity.IdGenerator;
import dataaccess.unitofwork.UnitOfWork;

import java.util.List;
import java.util.Optional;

/**
 * Data Mapper for orders and their dependent items. Foreign Key Mapping + Lazy Load: the order keeps
 * {@code customer_id}; the customer is read through the customer mapper (and so its Identity Map) only on the
 * first {@code getCustomer()}.
 */
public class OrderDataMapper implements OrderRepository {
    private final OrderGateway gateway;
    private final CustomerDataMapper customers;
    private final UnitOfWork unitOfWork;

    public OrderDataMapper(OrderGateway gateway, CustomerDataMapper customers, UnitOfWork unitOfWork) {
        this.gateway = gateway;
        this.customers = customers;
        this.unitOfWork = unitOfWork;
    }

    @Override
    public String nextId() {
        return IdGenerator.assign(null, "O");
    }

    @Override
    public Optional<Order> findById(String orderId) {
        return unitOfWork.get(Order.class, orderId).or(() -> gateway.findById(orderId).map(this::load));
    }

    @Override
    public List<Order> findAll() {
        return gateway.findAll().stream().map(this::identityOrLoad).toList();
    }

    @Override
    public void add(Order order) {
        attachCustomerLoader(order);
        unitOfWork.registerNew(order, order.getOrderId());
    }

    @Override
    public void update(Order order) {
        unitOfWork.registerDirty(order, order.getOrderId());
    }

    @Override
    public void remove(Order order) {
        unitOfWork.registerDeleted(order, order.getOrderId());
    }

    private Order identityOrLoad(OrderDto record) {
        return unitOfWork.get(Order.class, record.orderId()).orElseGet(() -> load(record));
    }

    private Order load(OrderDto record) {
        Order order = OrderMapper.toDomain(record);
        attachCustomerLoader(order);
        unitOfWork.remember(order, record.orderId());
        return order;
    }

    private void attachCustomerLoader(Order order) {
        String customerId = order.getCustomerId();
        order.setCustomerLoader(() -> customers.findById(customerId)
                .orElseThrow(() -> new IllegalStateException("Zákazník " + customerId + " neexistuje.")));
    }
}
