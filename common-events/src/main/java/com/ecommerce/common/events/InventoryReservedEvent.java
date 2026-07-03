package com.ecommerce.common.events;

import java.util.List;
import java.util.UUID;

public record InventoryReservedEvent(UUID orderId, UUID reservationId, List<ReservedLine> lines) {
    public record ReservedLine(UUID productId, int quantity) {}
}
