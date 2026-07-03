package com.ecommerce.order.outbox;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/**
 * Transactional Outbox: domain changes and their events are committed in the
 * SAME database transaction, then a relay publishes rows to Kafka.
 * This removes the classic dual-write inconsistency (DB committed but Kafka
 * publish lost, or vice versa).
 */
@Entity
@Table(name = "outbox_events")
public class OutboxEvent {

    @Id
    private UUID id;

    @Column(nullable = false)
    private UUID aggregateId;      // orderId — used as the Kafka message key for per-order ordering

    @Column(nullable = false)
    private String topic;

    @Column(nullable = false)
    private String eventType;

    @Column(nullable = false, columnDefinition = "text")
    private String payload;        // serialized EventEnvelope JSON

    @Column(nullable = false)
    private Instant createdAt;

    private Instant publishedAt;   // null => pending

    private int attempts;

    protected OutboxEvent() {}

    public OutboxEvent(UUID aggregateId, String topic, String eventType, String payload) {
        this.id = UUID.randomUUID();
        this.aggregateId = aggregateId;
        this.topic = topic;
        this.eventType = eventType;
        this.payload = payload;
        this.createdAt = Instant.now();
    }

    public void markPublished() { this.publishedAt = Instant.now(); }
    public void recordAttempt() { this.attempts++; }

    public UUID getId() { return id; }
    public UUID getAggregateId() { return aggregateId; }
    public String getTopic() { return topic; }
    public String getEventType() { return eventType; }
    public String getPayload() { return payload; }
    public int getAttempts() { return attempts; }
}
