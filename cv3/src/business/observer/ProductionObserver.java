package business.observer;

import business.domainmodel.Order;
import business.domainmodel.OrderStatus;
import lombok.RequiredArgsConstructor;

import java.util.function.Consumer;

/**
 * CV09 Observer: production prepares the work when an order is confirmed. The message goes to the given output
 * (the console in the application, a list in tests), so the business layer does no I/O itself.
 */
@RequiredArgsConstructor
public class ProductionObserver implements IOrderObserver {
    private final Consumer<String> output;

    @Override
    public void statusChanged(Order order, OrderStatus oldStatus, OrderStatus newStatus) {
        if (newStatus == OrderStatus.POTVRZENA) {
            output.accept("Objednávka " + order.getReference() + " potvrzena – připravit výrobu");
        }
    }
}
