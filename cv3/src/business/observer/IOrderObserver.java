package business.observer;

import business.domainmodel.Order;
import business.domainmodel.OrderStatus;

/** CV09 Observer: is told about a saved status change of an order (the Subject). */
public interface IOrderObserver {
    void statusChanged(Order order, OrderStatus oldStatus, OrderStatus newStatus);
}
