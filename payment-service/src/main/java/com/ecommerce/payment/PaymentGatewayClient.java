package com.ecommerce.payment;

import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Adapter around the external payment provider (Stripe/Razorpay/etc).
 * Resilience4j wraps the call: retry transient errors, bulkhead limits
 * concurrent calls, circuit breaker fails fast when the provider is down.
 */
@Component
public class PaymentGatewayClient {

    @CircuitBreaker(name = "paymentGateway", fallbackMethod = "gatewayDown")
    @Retry(name = "paymentGateway")
    @Bulkhead(name = "paymentGateway")
    public GatewayResult charge(UUID orderId, BigDecimal amount, String currency) {
        // PLACEHOLDER: replace with a real SDK call, e.g.
        //   PaymentIntent intent = PaymentIntent.create(Map.of(
        //       "amount", amount.movePointRight(2).longValueExact(),
        //       "currency", currency.toLowerCase(),
        //       "idempotency_key", orderId.toString()));
        //
        // Simulated gateway: ~85% success for demo purposes.
        if (ThreadLocalRandom.current().nextInt(100) < 85) {
            return GatewayResult.success("txn_" + UUID.randomUUID());
        }
        return GatewayResult.declined("Card declined by issuer");
    }

    @SuppressWarnings("unused")
    private GatewayResult gatewayDown(UUID orderId, BigDecimal amount, String currency, Throwable t) {
        return GatewayResult.unavailable("Payment provider unavailable: " + t.getMessage());
    }

    public record GatewayResult(boolean success, boolean retryable, String txRef, String reason) {
        static GatewayResult success(String txRef)      { return new GatewayResult(true, false, txRef, null); }
        static GatewayResult declined(String reason)    { return new GatewayResult(false, false, null, reason); }
        static GatewayResult unavailable(String reason) { return new GatewayResult(false, true, null, reason); }
    }
}
