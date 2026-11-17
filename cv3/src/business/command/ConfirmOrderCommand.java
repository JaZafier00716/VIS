package business.command;

import business.service.OrderService;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** CV09 Command: confirms an order through the service (the receiver). */
@RequiredArgsConstructor
public class ConfirmOrderCommand implements IOrderCommand {
    private final OrderService orderService;
    @Getter
    private final String orderId;

    @Override
    public String getName() {
        return "Potvrdit objednávku";
    }

    @Override
    public void execute() {
        orderService.confirmOrder(orderId);
    }
}
