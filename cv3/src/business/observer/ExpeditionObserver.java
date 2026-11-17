package business.observer;

import business.domainmodel.Order;
import business.domainmodel.OrderStatus;
import lombok.RequiredArgsConstructor;

import java.util.function.Consumer;

/** CV09 Observer: expedition learns that an order is ready to be shipped. */
@RequiredArgsConstructor
public class ExpeditionObserver implements IOrderObserver {
    private final Consumer<String> output;

    @Override
    public void statusChanged(Order order, OrderStatus oldStatus, OrderStatus newStatus) {
        if (newStatus == OrderStatus.PRIPRAVENA_K_EXPEDICI) {
            output.accept("Objednávka " + order.getReference() + " připravena k expedici");
        }
    }
}
