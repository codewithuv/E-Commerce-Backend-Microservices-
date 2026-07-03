package com.ecommerce.common.events;

import java.util.UUID;

public record InventoryReleasedEvent(UUID orderId, UUID reservationId) {}
