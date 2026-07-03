package com.ecommerce.common.events;

import java.time.Instant;
import java.util.UUID;

/**
 * Generic envelope wrapping every domain event.
 * - eventId enables idempotent consumption (exactly-once *effect*).
 * - sagaId (== orderId) correlates all steps of a single saga instance.
 * - traceId propagates distributed tracing context.
 */
public record EventEnvelope<T>(
        UUID eventId,
        UUID sagaId,
        String eventType,
        Instant occurredAt,
        String traceId,
        int schemaVersion,
        T payload
) {
    public static <T> EventEnvelope<T> of(UUID sagaId, String eventType, String traceId, T payload) {
        return new EventEnvelope<>(UUID.randomUUID(), sagaId, eventType, Instant.now(), traceId, 1, payload);
    }
}
