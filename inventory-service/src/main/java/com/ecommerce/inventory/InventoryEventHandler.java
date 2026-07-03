package com.ecommerce.inventory;

import com.ecommerce.common.events.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * Saga participant.
 *  - OrderCreated   -> try to reserve stock (all-or-nothing) -> InventoryReserved | InventoryRejected
 *  - OrderCancelled -> release held reservations (compensation)
 *  - OrderCompleted -> commit reservations into a hard stock decrement
 */
@Component
public class InventoryEventHandler {

    private static final Logger log = LoggerFactory.getLogger(InventoryEventHandler.class);

    private final InventoryRepository inventory;
    private final ReservationRepository reservations;
    private final KafkaTemplate<String, String> kafka;
    private final ObjectMapper mapper;

    public InventoryEventHandler(InventoryRepository inventory,
                                 ReservationRepository reservations,
                                 KafkaTemplate<String, String> kafka,
                                 ObjectMapper mapper) {
        this.inventory = inventory;
        this.reservations = reservations;
        this.kafka = kafka;
        this.mapper = mapper;
    }

    @KafkaListener(topics = KafkaTopics.ORDER_CREATED, groupId = "inventory-service")
    @Transactional
    public void onOrderCreated(String message) throws Exception {
        EventEnvelope<OrderCreatedEvent> env = mapper.readValue(message,
                mapper.getTypeFactory().constructParametricType(EventEnvelope.class, OrderCreatedEvent.class));
        OrderCreatedEvent event = env.payload();

        List<Reservation> held = new ArrayList<>();
        try {
            // All-or-nothing: any line failing rolls back every reservation in this tx.
            for (var line : event.lines()) {
                InventoryItem item = inventory.findForUpdate(line.productId())
                        .orElseThrow(() -> new IllegalStateException("Unknown product " + line.productId()));
                item.reserve(line.quantity());
                held.add(reservations.save(new Reservation(event.orderId(), line.productId(), line.quantity())));
            }

            var reserved = new InventoryReservedEvent(
                    event.orderId(),
                    held.get(0).getId(),
                    held.stream().map(r -> new InventoryReservedEvent.ReservedLine(r.getProductId(), r.getQuantity())).toList());

            publish(KafkaTopics.INVENTORY_RESERVED, env, "InventoryReserved", reserved);
            log.info("Reserved stock for order {}", event.orderId());

        } catch (IllegalStateException e) {
            // Transaction rolls back reservations; emit rejection so the saga cancels.
            publish(KafkaTopics.INVENTORY_REJECTED, env, "InventoryRejected",
                    new InventoryRejectedEvent(event.orderId(), e.getMessage()));
            log.warn("Rejected stock reservation for order {}: {}", event.orderId(), e.getMessage());
        }
    }

    @KafkaListener(topics = KafkaTopics.ORDER_CANCELLED, groupId = "inventory-service")
    @Transactional
    public void onOrderCancelled(String message) throws Exception {
        EventEnvelope<OrderCancelledEvent> env = mapper.readValue(message,
                mapper.getTypeFactory().constructParametricType(EventEnvelope.class, OrderCancelledEvent.class));

        var held = reservations.findByOrderIdAndStatus(env.payload().orderId(), Reservation.Status.HELD);
        for (Reservation r : held) {
            inventory.findForUpdate(r.getProductId()).ifPresent(item -> item.release(r.getQuantity()));
            r.release();
        }
        if (!held.isEmpty()) {
            publish(KafkaTopics.INVENTORY_RELEASED, env, "InventoryReleased",
                    new InventoryReleasedEvent(env.payload().orderId(), held.get(0).getId()));
            log.info("Compensation: released {} reservation(s) for cancelled order {}", held.size(), env.payload().orderId());
        }
    }

    @KafkaListener(topics = KafkaTopics.ORDER_COMPLETED, groupId = "inventory-service")
    @Transactional
    public void onOrderCompleted(String message) throws Exception {
        EventEnvelope<OrderCompletedEvent> env = mapper.readValue(message,
                mapper.getTypeFactory().constructParametricType(EventEnvelope.class, OrderCompletedEvent.class));

        var held = reservations.findByOrderIdAndStatus(env.payload().orderId(), Reservation.Status.HELD);
        for (Reservation r : held) {
            inventory.findForUpdate(r.getProductId()).ifPresent(item -> item.commitReservation(r.getQuantity()));
            r.commit();
        }
        log.info("Committed reservations for completed order {}", env.payload().orderId());
    }

    private <T> void publish(String topic, EventEnvelope<?> parent, String type, T payload) throws Exception {
        var envelope = EventEnvelope.of(parent.sagaId(), type, parent.traceId(), payload);
        kafka.send(topic, parent.sagaId().toString(), mapper.writeValueAsString(envelope));
    }
}
