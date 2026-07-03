package com.ecommerce.inventory;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/** Tracks what was reserved per order so compensation can release exactly that. */
@Entity
@Table(name = "reservations")
public class Reservation {

    @Id
    private UUID id;

    @Column(nullable = false)
    private UUID orderId;

    @Column(nullable = false)
    private UUID productId;

    @Column(nullable = false)
    private int quantity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status;

    @Column(nullable = false)
    private Instant createdAt;

    public enum Status { HELD, RELEASED, COMMITTED }

    protected Reservation() {}

    public Reservation(UUID orderId, UUID productId, int quantity) {
        this.id = UUID.randomUUID();
        this.orderId = orderId;
        this.productId = productId;
        this.quantity = quantity;
        this.status = Status.HELD;
        this.createdAt = Instant.now();
    }

    public void release()  { this.status = Status.RELEASED; }
    public void commit()   { this.status = Status.COMMITTED; }

    public UUID getId() { return id; }
    public UUID getOrderId() { return orderId; }
    public UUID getProductId() { return productId; }
    public int getQuantity() { return quantity; }
    public Status getStatus() { return status; }
}
