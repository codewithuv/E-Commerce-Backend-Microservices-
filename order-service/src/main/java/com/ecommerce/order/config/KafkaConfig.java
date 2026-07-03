package com.ecommerce.order.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.ExponentialBackOff;

import static com.ecommerce.common.events.KafkaTopics.*;

@Configuration
public class KafkaConfig {

    /**
     * Retries with exponential backoff, then routes the poison message to
     * <topic>.DLT so one bad payload never blocks the partition.
     */
    @Bean
    public DefaultErrorHandler errorHandler(KafkaTemplate<String, String> template) {
        var backOff = new ExponentialBackOff(1_000L, 2.0);
        backOff.setMaxElapsedTime(30_000L);
        return new DefaultErrorHandler(new DeadLetterPublishingRecoverer(template), backOff);
    }

    @Bean public NewTopic orderCreated()   { return TopicBuilder.name(ORDER_CREATED).partitions(6).replicas(1).build(); }
    @Bean public NewTopic orderCompleted() { return TopicBuilder.name(ORDER_COMPLETED).partitions(6).replicas(1).build(); }
    @Bean public NewTopic orderCancelled() { return TopicBuilder.name(ORDER_CANCELLED).partitions(6).replicas(1).build(); }
}
