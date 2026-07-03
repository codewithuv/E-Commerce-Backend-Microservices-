package com.ecommerce.order.outbox;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.concurrent.TimeUnit;

/**
 * Polling relay that drains the outbox table into Kafka.
 * (In production you can swap this for Debezium CDC — the write path stays identical.)
 */
@Component
public class OutboxRelay {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);

    private final OutboxRepository repository;
    private final KafkaTemplate<String, String> kafkaTemplate;

    public OutboxRelay(OutboxRepository repository, KafkaTemplate<String, String> kafkaTemplate) {
        this.repository = repository;
        this.kafkaTemplate = kafkaTemplate;
    }

    @Scheduled(fixedDelayString = "${outbox.relay.poll-interval-ms:500}")
    @Transactional
    public void publishPendingEvents() {
        var batch = repository.findBatchToPublish();
        for (OutboxEvent event : batch) {
            event.recordAttempt();
            try {
                // Key = aggregateId => all events of one order land on the same
                // partition, preserving per-order ordering.
                kafkaTemplate.send(event.getTopic(), event.getAggregateId().toString(), event.getPayload())
                        .get(5, TimeUnit.SECONDS);
                event.markPublished();
                log.info("Published outbox event {} to {}", event.getEventType(), event.getTopic());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.error("Interrupted while publishing outbox event {}", event.getId(), e);
                return;
            } catch (Exception e) {
                log.error("Failed to publish outbox event {} (attempt {})", event.getId(), event.getAttempts(), e);
                // row stays pending; retried next poll with attempt cap
            }
        }
    }
}
