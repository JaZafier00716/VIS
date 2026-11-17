package dataaccess.persistence;

import dataaccess.row.AddressRow;
import dataaccess.row.OrderRow;

import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * What the H2 foreign keys to the customer root table do, done for JSON by whichever root table the strategy
 * uses (customers.json for CTI, customers_sti.json for STI): orders block the delete (FK RESTRICT) and the
 * customer's addresses are removed with it (ON DELETE CASCADE).
 */
final class JsonCustomerReferences {
    private JsonCustomerReferences() {}

    static void requireNoOrders(String customerId) {
        List<OrderRow> orders = JsonFiles.read(JsonOrderGateway.FILE, JsonOrderGateway.TYPE);
        if (orders.stream().anyMatch(order -> order.customerId().equals(customerId))) {
            throw new IllegalStateException("Zákazníka " + customerId + " nelze smazat, má objednávky.");
        }
    }

    static void deleteAddresses(String customerId) {
        List<AddressRow> addresses = JsonFiles.read(JsonAddressGateway.FILE, JsonAddressGateway.TYPE);
        if (addresses.removeIf(address -> address.customerId().equals(customerId))) {
            JsonFiles.write(JsonAddressGateway.FILE, addresses);
        }
    }

    /** All four billing columns or none, like the H2 CHECK constraints *_billing. */
    static void checkBilling(String street, String city, String zipCode, String country) {
        long filled = Stream.of(street, city, zipCode, country).filter(Objects::nonNull).count();
        if (filled != 0 && filled != 4) {
            throw new IllegalArgumentException("Fakturační adresa musí být vyplněna celá, nebo vůbec.");
        }
    }

    static void checkType(String type) {
        if (!"P".equals(type) && !"C".equals(type)) {
            throw new IllegalArgumentException("Neplatný typ zákazníka: " + type);
        }
    }
}
