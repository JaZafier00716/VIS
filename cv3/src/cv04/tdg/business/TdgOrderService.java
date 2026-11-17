package cv04.tdg.business;

import cv04.Cv04Schema;
import cv04.tdg.dataaccess.CustomerDto;
import cv04.tdg.dataaccess.CustomerTableGateway;
import cv04.tdg.dataaccess.MaterialTableGateway;
import cv04.tdg.dataaccess.OrderDto;
import cv04.tdg.dataaccess.OrderTableGateway;
import dataaccess.h2.H2Database;

import java.time.Instant;
import java.util.UUID;

/**
 * CV04 TDG step 4, the application service (BL role): builds the DTOs, validates them (dimensions &gt; 0, the
 * material exists), sets the status CEKA_NA_POTVRZENI and calls the gateways' {@code insert}. The gateways
 * contain the SQL and nothing else.
 */
public class TdgOrderService {
    private final CustomerTableGateway customers;
    private final MaterialTableGateway materials;
    private final OrderTableGateway orders;

    public TdgOrderService(H2Database database) {
        this.customers = new CustomerTableGateway(database);
        this.materials = new MaterialTableGateway(database);
        this.orders = new OrderTableGateway(database);
    }

    /** Everything is validated before the first insert, so a rejected order stores nothing. */
    public OrderDto placeOrder(String customerName, String email, String materialId, double width, double height,
                               double depth, String designNotes) {
        if (customerName == null || customerName.isBlank()) throw new IllegalArgumentException("Jméno je povinné.");
        if (email == null || !email.contains("@")) throw new IllegalArgumentException("E-mail není platný.");
        if (width <= 0 || height <= 0 || depth <= 0) {
            throw new IllegalArgumentException("Rozměry musí být větší než 0.");
        }
        if (materials.findById(materialId).isEmpty()) {
            throw new IllegalArgumentException("Materiál " + materialId + " neexistuje.");
        }

        CustomerDto customer = new CustomerDto("C-" + UUID.randomUUID(), customerName, email);
        OrderDto order = new OrderDto("O-" + UUID.randomUUID(), customer.customerId(), materialId, width, height,
                depth, designNotes, Cv04Schema.STATUS_WAITING, Instant.now());
        customers.insert(customer);
        orders.insert(order);
        // Step 5: confirmation read back through FindById.
        return orders.findById(order.orderId()).orElseThrow();
    }
}
