package business.repository;

/** The repositories of one application, all registering into the same Unit of Work. */
public record Repositories(CustomerRepository customers,
                           AddressRepository addresses,
                           MaterialRepository materials,
                           FinishRepository finishes,
                           OrderRepository orders) {
}
