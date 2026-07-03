package com.ecommerce.common.events;

import java.util.UUID;

public record OrderCancelledEvent(UUID orderId, UUID customerId, String reason) {}
