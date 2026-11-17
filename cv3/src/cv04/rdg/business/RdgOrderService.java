package cv04.rdg.business;

import cv04.Cv04Schema;
import cv04.rdg.dataaccess.CustomerRowGateway;
import cv04.rdg.dataaccess.MaterialRowGateway;
import cv04.rdg.dataaccess.OrderRowGateway;
import dataaccess.h2.H2Database;

import java.time.Instant;
import java.util.UUID;

/**
 * CV04 RDG step 4–5 (BL role): validates the input, then creates a CustomerRowGateway → insert() and an
 * OrderRowGateway in state CEKA_NA_POTVRZENI → insert(). The SQL lives in the row objects.
 */
public class RdgOrderService {
    private final H2Database database;

    public RdgOrderService(H2Database database) {
        this.database = database;
    }

    /** Everything is validated before the first insert, so a rejected order stores nothing. */
    public OrderRowGateway placeOrder(String customerName, String email, String materialId, double width,
                                      double height, double depth, String designNotes) {
        if (customerName == null || customerName.isBlank()) throw new IllegalArgumentException("Jméno je povinné.");
        if (email == null || !email.contains("@")) throw new IllegalArgumentException("E-mail není platný.");
        if (width <= 0 || height <= 0 || depth <= 0) {
            throw new IllegalArgumentException("Rozměry musí být větší než 0.");
        }
        if (MaterialRowGateway.find(database, materialId).isEmpty()) {
            throw new IllegalArgumentException("Materiál " + materialId + " neexistuje.");
        }

        CustomerRowGateway customer = new CustomerRowGateway(database, "C-" + UUID.randomUUID(), customerName, email);
        customer.insert();
        OrderRowGateway order = new OrderRowGateway(database, "O-" + UUID.randomUUID(), customer.getCustomerId(),
                materialId, width, height, depth, designNotes, Cv04Schema.STATUS_WAITING, Instant.now());
        order.insert();
        return OrderRowGateway.find(database, order.getOrderId()).orElseThrow();
    }
}
