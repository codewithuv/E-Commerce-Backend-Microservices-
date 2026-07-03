package com.ecommerce.order.saga;

import com.ecommerce.common.events.*;
import com.ecommerce.order.domain.Order;
import com.ecommerce.order.domain.OrderRepository;
import com.ecommerce.order.messaging.ProcessedEvent;
import com.ecommerce.order.messaging.ProcessedEventRepository;
import com.ecommerce.order.outbox.OutboxService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Choreography-based saga: the order service reacts to inventory/payment
 * events and advances (or compensates) the order state machine.
 *
 * Happy path:
 *   OrderCreated -> InventoryReserved -> PaymentProcessed -> OrderCompleted
 *
 * Compensation paths:
 *   InventoryRejected  -> OrderCancelled
 *   PaymentFailed      -> OrderCancelled + InventoryReleased (compensating action)
 */
@Component
public class OrderSagaHandler {

    private static final Logger log = LoggerFactory.getLogger(OrderSagaHandler.class);

    private final OrderRepository orders;
    private final ProcessedEventRepository processedEvents;
    private final OutboxService outbox;
    private final ObjectMapper mapper;

    public OrderSagaHandler(OrderRepository orders,
                            ProcessedEventRepository processedEvents,
                            OutboxService outbox,
                            ObjectMapper mapper) {
        this.orders = orders;
        this.processedEvents = processedEvents;
        this.outbox = outbox;
        this.mapper = mapper;
    }

    @KafkaListener(topics = KafkaTopics.INVENTORY_RESERVED, groupId = "order-service")
    @Transactional
    public void onInventoryReserved(String message) throws Exception {
        var envelope = mapper.readValue(message,
                mapper.getTypeFactory().constructParametricType(EventEnvelope.class, InventoryReservedEvent.class));
        EventEnvelope<InventoryReservedEvent> env = (EventEnvelope<InventoryReservedEvent>) envelope;

        if (alreadyProcessed(env.eventId())) return;

        Order order = orders.findById(env.payload().orderId()).orElseThrow();
        order.markInventoryReserved();
        log.info("Saga[{}]: inventory reserved, awaiting payment", order.getId());
        // Payment service listens to INVENTORY_RESERVED directly — no command needed here.
    }

    @KafkaListener(topics = KafkaTopics.PAYMENT_PROCESSED, groupId = "order-service")
    @Transactional
    public void onPaymentProcessed(String message) throws Exception {
        EventEnvelope<PaymentProcessedEvent> env = mapper.readValue(message,
                mapper.getTypeFactory().constructParametricType(EventEnvelope.class, PaymentProcessedEvent.class));

        if (alreadyProcessed(env.eventId())) return;

        Order order = orders.findById(env.payload().orderId()).orElseThrow();
        order.markPaid();
        order.complete();

        outbox.append(order.getId(), KafkaTopics.ORDER_COMPLETED, "OrderCompleted",
                new OrderCompletedEvent(order.getId(), order.getCustomerId()));
        log.info("Saga[{}]: COMPLETED", order.getId());
    }

    @KafkaListener(topics = KafkaTopics.INVENTORY_REJECTED, groupId = "order-service")
    @Transactional
    public void onInventoryRejected(String message) throws Exception {
        EventEnvelope<InventoryRejectedEvent> env = mapper.readValue(message,
                mapper.getTypeFactory().constructParametricType(EventEnvelope.class, InventoryRejectedEvent.class));

        if (alreadyProcessed(env.eventId())) return;

        Order order = orders.findById(env.payload().orderId()).orElseThrow();
        order.cancel("Inventory rejected: " + env.payload().reason());

        outbox.append(order.getId(), KafkaTopics.ORDER_CANCELLED, "OrderCancelled",
                new OrderCancelledEvent(order.getId(), order.getCustomerId(), order.getCancellationReason()));
        log.warn("Saga[{}]: CANCELLED — {}", order.getId(), env.payload().reason());
    }

    @KafkaListener(topics = KafkaTopics.PAYMENT_FAILED, groupId = "order-service")
    @Transactional
    public void onPaymentFailed(String message) throws Exception {
        EventEnvelope<PaymentFailedEvent> env = mapper.readValue(message,
                mapper.getTypeFactory().constructParametricType(EventEnvelope.class, PaymentFailedEvent.class));

        if (alreadyProcessed(env.eventId())) return;

        Order order = orders.findById(env.payload().orderId()).orElseThrow();
        order.cancel("Payment failed: " + env.payload().reason());

        // Compensating event: inventory service releases the reservation.
        outbox.append(order.getId(), KafkaTopics.ORDER_CANCELLED, "OrderCancelled",
                new OrderCancelledEvent(order.getId(), order.getCustomerId(), order.getCancellationReason()));
        log.warn("Saga[{}]: CANCELLED (payment failed) — compensation triggered", order.getId());
    }

    /** Inserts eventId into the ledger; returns true if it was already there. */
    private boolean alreadyProcessed(UUID eventId) {
        if (processedEvents.existsById(eventId)) {
            log.debug("Duplicate delivery of event {} — skipping", eventId);
            return true;
        }
        processedEvents.save(new ProcessedEvent(eventId));
        return false;
    }
}
