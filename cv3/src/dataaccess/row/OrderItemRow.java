package dataaccess.row;

import java.math.BigDecimal;

/** JSON record of a dependent order item; the owner is identified by orderId (Dependent Mapping). */
public record OrderItemRow(String orderId, int lineNumber, String materialId, int quantity,
                           BigDecimal unitPrice, String configurationJson) {}
