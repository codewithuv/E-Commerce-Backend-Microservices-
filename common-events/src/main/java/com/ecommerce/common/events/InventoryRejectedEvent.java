package com.ecommerce.common.events;

import java.util.UUID;

public record InventoryRejectedEvent(UUID orderId, String reason) {}
