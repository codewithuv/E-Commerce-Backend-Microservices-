package com.ecommerce.order.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "orders")
public class Order {

    @Id
    private UUID id;

    @Column(nullable = false)
    private UUID customerId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrderStatus status;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal totalAmount;

    @Column(nullable = false, length = 3)
    private String currency;

    private String cancellationReason;

    @Column(nullable = false)
    private Instant createdAt;

    private Instant updatedAt;

    /** Optimistic locking guards against concurrent saga transitions. */
    @Version
    private long version;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    private List<OrderItem> items = new ArrayList<>();

    protected Order() {}

    public static Order create(UUID customerId, String currency, List<OrderItem> items) {
        Order order = new Order();
        order.id = UUID.randomUUID();
        order.customerId = customerId;
        order.currency = currency;
        order.status = OrderStatus.PENDING;
        order.createdAt = Instant.now();
        items.forEach(i -> {
            i.setOrder(order);
            order.items.add(i);
        });
        order.totalAmount = items.stream()
                .map(i -> i.getUnitPrice().multiply(BigDecimal.valueOf(i.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return order;
    }

    /* --- Saga state transitions (invariants enforced here, not in services) --- */

    public void markInventoryReserved() {
        require(status == OrderStatus.PENDING, "inventory can only be reserved on a PENDING order");
        transition(OrderStatus.INVENTORY_RESERVED);
    }

    public void markPaid() {
        require(status == OrderStatus.INVENTORY_RESERVED, "payment requires reserved inventory");
        transition(OrderStatus.PAID);
    }

    public void complete() {
        require(status == OrderStatus.PAID, "only paid orders can complete");
        transition(OrderStatus.COMPLETED);
    }

    public void cancel(String reason) {
        require(status != OrderStatus.COMPLETED, "completed orders cannot be cancelled");
        this.cancellationReason = reason;
        transition(OrderStatus.CANCELLED);
    }

    private void transition(OrderStatus next) {
        this.status = next;
        this.updatedAt = Instant.now();
    }

    private void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(
                "Invalid saga transition on order %s (status=%s): %s".formatted(id, status, message));
    }

    /* --- getters --- */
    public UUID getId() { return id; }
    public UUID getCustomerId() { return customerId; }
    public OrderStatus getStatus() { return status; }
    public BigDecimal getTotalAmount() { return totalAmount; }
    public String getCurrency() { return currency; }
    public List<OrderItem> getItems() { return items; }
    public Instant getCreatedAt() { return createdAt; }
    public String getCancellationReason() { return cancellationReason; }
}
