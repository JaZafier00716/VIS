package dataaccess.dto;

import java.math.BigDecimal;

public record OrderItemDto(int lineNumber, String materialId, int quantity, BigDecimal unitPrice,
                           String configurationJson) {}
