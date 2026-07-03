package com.ecommerce.common.events;

import java.util.UUID;

public record PaymentFailedEvent(UUID orderId, String reason, boolean retryable) {}
