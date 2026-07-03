package com.ecommerce.common.events;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record OrderCreatedEvent(
        UUID orderId,
        UUID customerId,
        BigDecimal totalAmount,
        String currency,
        List<OrderLine> lines
) {
    public record OrderLine(UUID productId, String sku, int quantity, BigDecimal unitPrice) {}
}
