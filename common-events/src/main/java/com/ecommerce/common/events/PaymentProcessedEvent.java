package com.ecommerce.common.events;

import java.math.BigDecimal;
import java.util.UUID;

public record PaymentProcessedEvent(UUID orderId, UUID paymentId, BigDecimal amount, String transactionRef) {}
