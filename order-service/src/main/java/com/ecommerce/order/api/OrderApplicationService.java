package com.ecommerce.order.api;

import com.ecommerce.common.events.KafkaTopics;
import com.ecommerce.common.events.OrderCreatedEvent;
import com.ecommerce.order.domain.Order;
import com.ecommerce.order.domain.OrderItem;
import com.ecommerce.order.domain.OrderRepository;
import com.ecommerce.order.outbox.OutboxService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderApplicationService {

    private final OrderRepository orders;
    private final OutboxService outbox;

    public OrderApplicationService(OrderRepository orders, OutboxService outbox) {
        this.orders = orders;
        this.outbox = outbox;
    }

    /**
     * Single ACID transaction: persist order + outbox row.
     * The relay publishes OrderCreated afterwards — the saga starts from there.
     */
    @Transactional
    public Order placeOrder(CreateOrderRequest request, String idempotencyKey) {
        // PLACEHOLDER: check idempotencyKey against a request-dedup store (e.g. Redis SETNX)

        Order order = Order.create(
                request.customerId(),
                request.currency(),
                request.lines().stream()
                        .map(l -> new OrderItem(l.productId(), l.sku(), l.quantity(), l.unitPrice()))
                        .toList());

        orders.save(order);

        outbox.append(order.getId(), KafkaTopics.ORDER_CREATED, "OrderCreated",
                new OrderCreatedEvent(
                        order.getId(),
                        order.getCustomerId(),
                        order.getTotalAmount(),
                        order.getCurrency(),
                        order.getItems().stream()
                                .map(i -> new OrderCreatedEvent.OrderLine(i.getProductId(), i.getSku(), i.getQuantity(), i.getUnitPrice()))
                                .toList()));
        return order;
    }
}
