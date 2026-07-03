package com.ecommerce.common.events;

import java.util.UUID;

public record OrderCompletedEvent(UUID orderId, UUID customerId) {}
