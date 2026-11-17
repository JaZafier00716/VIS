package business.command;

import business.service.OrderService;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** CV09 Command: starts the production of a confirmed order. */
@RequiredArgsConstructor
public class StartProductionCommand implements IOrderCommand {
    private final OrderService orderService;
    @Getter
    private final String orderId;

    @Override
    public String getName() {
        return "Zahájit výrobu";
    }

    @Override
    public void execute() {
        orderService.startProduction(orderId);
    }
}
