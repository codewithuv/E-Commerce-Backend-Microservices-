package com.ecommerce.payment;

import com.ecommerce.common.events.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Saga participant. Triggered by InventoryReserved: attempts to charge the
 * customer and reports back PaymentProcessed / PaymentFailed.
 */
@Component
public class PaymentEventHandler {

    private static final Logger log = LoggerFactory.getLogger(PaymentEventHandler.class);

    private final PaymentRepository payments;
    private final PaymentGatewayClient gateway;
    private final KafkaTemplate<String, String> kafka;
    private final ObjectMapper mapper;

    public PaymentEventHandler(PaymentRepository payments,
                               PaymentGatewayClient gateway,
                               KafkaTemplate<String, String> kafka,
                               ObjectMapper mapper) {
        this.payments = payments;
        this.gateway = gateway;
        this.kafka = kafka;
        this.mapper = mapper;
    }

    @KafkaListener(topics = KafkaTopics.INVENTORY_RESERVED, groupId = "payment-service")
    @Transactional
    public void onInventoryReserved(String message) throws Exception {
        EventEnvelope<InventoryReservedEvent> env = mapper.readValue(message,
                mapper.getTypeFactory().constructParametricType(EventEnvelope.class, InventoryReservedEvent.class));
        var orderId = env.payload().orderId();

        // Idempotency: unique(orderId) means redelivery can't double-charge.
        if (payments.findByOrderId(orderId).isPresent()) {
            log.info("Payment for order {} already processed — skipping duplicate", orderId);
            return;
        }

        // PLACEHOLDER: amount should be looked up/validated against the order
        // (e.g. carried in the event or fetched via an internal endpoint).
        var amount = new java.math.BigDecimal("99.99");

        var result = gateway.charge(orderId, amount, "USD");
        if (result.success()) {
            var payment = payments.save(Payment.authorized(orderId, amount, result.txRef()));
            publish(KafkaTopics.PAYMENT_PROCESSED, env, "PaymentProcessed",
                    new PaymentProcessedEvent(orderId, payment.getId(), amount, result.txRef()));
            log.info("Charged order {} — txRef {}", orderId, result.txRef());
        } else {
            payments.save(Payment.failed(orderId, amount, result.reason()));
            publish(KafkaTopics.PAYMENT_FAILED, env, "PaymentFailed",
                    new PaymentFailedEvent(orderId, result.reason(), result.retryable()));
            log.warn("Payment failed for order {}: {}", orderId, result.reason());
        }
    }

    private <T> void publish(String topic, EventEnvelope<?> parent, String type, T payload) throws Exception {
        var envelope = EventEnvelope.of(parent.sagaId(), type, parent.traceId(), payload);
        kafka.send(topic, parent.sagaId().toString(), mapper.writeValueAsString(envelope));
    }
}
