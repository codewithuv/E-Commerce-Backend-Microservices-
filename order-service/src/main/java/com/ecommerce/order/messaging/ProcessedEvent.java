package com.ecommerce.order.messaging;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * Idempotency ledger. Kafka guarantees at-least-once delivery, so consumers
 * must deduplicate. Inserting the eventId (primary key) inside the same
 * transaction as the state change makes redelivery a no-op.
 */
@Entity
@Table(name = "processed_events")
public class ProcessedEvent {

    @Id
    private UUID eventId;
    private Instant processedAt;

    protected ProcessedEvent() {}

    public ProcessedEvent(UUID eventId) {
        this.eventId = eventId;
        this.processedAt = Instant.now();
    }
}
