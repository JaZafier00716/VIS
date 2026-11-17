package business.command;

/** CV09 Command: one order operation packed as an object, so it can be run and logged uniformly. */
public interface IOrderCommand {
    String getName();

    String getOrderId();

    void execute();
}
