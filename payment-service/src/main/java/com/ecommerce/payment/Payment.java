package com.ecommerce.payment;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "payments",
       uniqueConstraints = @UniqueConstraint(columnNames = "orderId")) // one payment per order — DB-level idempotency
public class Payment {

    @Id
    private UUID id;

    @Column(nullable = false)
    private UUID orderId;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status;

    private String gatewayTransactionRef;
    private String failureReason;

    @Column(nullable = false)
    private Instant createdAt;

    public enum Status { AUTHORIZED, FAILED }

    protected Payment() {}

    public static Payment authorized(UUID orderId, BigDecimal amount, String txRef) {
        Payment p = base(orderId, amount);
        p.status = Status.AUTHORIZED;
        p.gatewayTransactionRef = txRef;
        return p;
    }

    public static Payment failed(UUID orderId, BigDecimal amount, String reason) {
        Payment p = base(orderId, amount);
        p.status = Status.FAILED;
        p.failureReason = reason;
        return p;
    }

    private static Payment base(UUID orderId, BigDecimal amount) {
        Payment p = new Payment();
        p.id = UUID.randomUUID();
        p.orderId = orderId;
        p.amount = amount;
        p.createdAt = Instant.now();
        return p;
    }

    public UUID getId() { return id; }
    public Status getStatus() { return status; }
    public String getGatewayTransactionRef() { return gatewayTransactionRef; }
}
