package com.ecommerce.order.outbox;

import com.ecommerce.common.events.EventEnvelope;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Called from within a domain transaction — the outbox row commits atomically
 * with the aggregate change.
 */
@Service
public class OutboxService {

    private final OutboxRepository repository;
    private final ObjectMapper objectMapper;

    public OutboxService(OutboxRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    public <T> void append(UUID sagaId, String topic, String eventType, T payload) {
        try {
            EventEnvelope<T> envelope = EventEnvelope.of(sagaId, eventType, currentTraceId(), payload);
            String json = objectMapper.writeValueAsString(envelope);
            repository.save(new OutboxEvent(sagaId, topic, eventType, json));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize outbox event " + eventType, e);
        }
    }

    private String currentTraceId() {
        // PLACEHOLDER: pull from Micrometer Tracing / OpenTelemetry context
        return UUID.randomUUID().toString();
    }
}
