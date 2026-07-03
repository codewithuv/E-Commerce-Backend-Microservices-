package com.ecommerce.order.domain;

/**
 * Saga state machine for an order:
 *
 *  PENDING -> INVENTORY_RESERVED -> PAID -> COMPLETED
 *     |               |
 *     v               v
 *  CANCELLED (compensated path)
 */
public enum OrderStatus {
    PENDING,
    INVENTORY_RESERVED,
    PAID,
    COMPLETED,
    CANCELLED
}
