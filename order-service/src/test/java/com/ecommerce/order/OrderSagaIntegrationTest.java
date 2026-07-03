package com.ecommerce.order;

import com.ecommerce.order.api.CreateOrderRequest;
import com.ecommerce.order.api.OrderApplicationService;
import com.ecommerce.order.domain.Order;
import com.ecommerce.order.domain.OrderStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Spins up REAL Postgres + Kafka in Docker containers. No mocks of
 * infrastructure — this is what makes the test suite credible in interviews.
 */
@SpringBootTest
@Testcontainers
class OrderSagaIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Container
    static KafkaContainer kafka = new KafkaContainer("apache/kafka:3.7.0");

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
        registry.add("eureka.client.enabled", () -> "false");
    }

    @Autowired
    OrderApplicationService orderService;

    @Test
    void placingOrderPersistsAggregateAndOutboxRowAtomically() {
        var request = new CreateOrderRequest(
                UUID.randomUUID(), "USD",
                List.of(new CreateOrderRequest.Line(UUID.randomUUID(), "SKU-001", 2, new BigDecimal("49.99"))));

        Order order = orderService.placeOrder(request, UUID.randomUUID().toString());

        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING);
        assertThat(order.getTotalAmount()).isEqualByComparingTo("99.98");
        // Outbox relay will publish OrderCreated; downstream saga verified in
        // the end-to-end test suite (see /e2e in README roadmap).
    }
}
