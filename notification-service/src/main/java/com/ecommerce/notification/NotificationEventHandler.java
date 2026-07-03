package com.ecommerce.notification;

import com.ecommerce.common.events.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Fire-and-forget consumer of terminal saga events. Deliberately has no
 * effect on the saga itself — notification failures must never roll back
 * an order.
 */
@Component
public class NotificationEventHandler {

    private static final Logger log = LoggerFactory.getLogger(NotificationEventHandler.class);

    private final EmailSender emailSender;
    private final ObjectMapper mapper;

    public NotificationEventHandler(EmailSender emailSender, ObjectMapper mapper) {
        this.emailSender = emailSender;
        this.mapper = mapper;
    }

    @KafkaListener(topics = KafkaTopics.ORDER_COMPLETED, groupId = "notification-service")
    public void onOrderCompleted(String message) throws Exception {
        EventEnvelope<OrderCompletedEvent> env = mapper.readValue(message,
                mapper.getTypeFactory().constructParametricType(EventEnvelope.class, OrderCompletedEvent.class));
        emailSender.send(env.payload().customerId(),
                "Your order is confirmed 🎉",
                "Order %s has been paid and confirmed.".formatted(env.payload().orderId()));
    }

    @KafkaListener(topics = KafkaTopics.ORDER_CANCELLED, groupId = "notification-service")
    public void onOrderCancelled(String message) throws Exception {
        EventEnvelope<OrderCancelledEvent> env = mapper.readValue(message,
                mapper.getTypeFactory().constructParametricType(EventEnvelope.class, OrderCancelledEvent.class));
        emailSender.send(env.payload().customerId(),
                "Your order was cancelled",
                "Order %s was cancelled. Reason: %s".formatted(env.payload().orderId(), env.payload().reason()));
    }
}
