package business.command;

import business.service.OrderService;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** CV09 Command: marks a produced order as ready for expedition. */
@RequiredArgsConstructor
public class PrepareExpeditionCommand implements IOrderCommand {
    private final OrderService orderService;
    @Getter
    private final String orderId;

    @Override
    public String getName() {
        return "Připravit k expedici";
    }

    @Override
    public void execute() {
        orderService.prepareForExpedition(orderId);
    }
}
