# payment-service

A **saga participant** that charges the customer. It reacts to `InventoryReserved`, calls an external
payment gateway (wrapped in **Resilience4j**), and reports back `PaymentProcessed` or `PaymentFailed`.
Event-driven — no public REST API.

- **Maven module:** `payment-service` · **Package:** `com.ecommerce.payment`
- **Port:** **8083** · DB: PostgreSQL `payments_db` (port 5434)
- **Docker:** `eclipse-temurin:17-jre-alpine`, `payment-service-1.0.0.jar`, `EXPOSE 8083`

---

## Package tour

### `PaymentServiceApplication.java`
Standard `@SpringBootApplication` main.

### `PaymentEventHandler.java` — core logic
`onInventoryReserved` — `@KafkaListener(topics = INVENTORY_RESERVED, groupId = "payment-service")`,
`@Transactional`:
1. **Idempotency guard** — `if payments.findByOrderId(orderId).isPresent()` → return. Combined with a
   DB `UNIQUE(order_id)` constraint, a redelivery can **never double-charge**.
2. Calls `gateway.charge(orderId, amount, "USD")`.
   - success → save `Payment.authorized(...)`, emit `PaymentProcessed` → `PAYMENT_PROCESSED`
   - failure → save `Payment.failed(...)`, emit `PaymentFailed` (with `retryable` flag) → `PAYMENT_FAILED`

> ⚠️ **Placeholder:** the charge amount is hard-coded `99.99`. In real use it should come from the event
> or an internal order-lookup call.

### `Payment.java` (`payments`)
Entity with a **`UNIQUE(orderId)`** constraint — one payment per order, the DB-level idempotency
backstop. Fields: `amount`, `status` (`AUTHORIZED | FAILED`), `gatewayTransactionRef`, `failureReason`.
Factory methods `authorized(...)` / `failed(...)`.

### `PaymentGatewayClient.java` — the resilient adapter
`charge(...)` is annotated with Resilience4j:
- `@CircuitBreaker(name="paymentGateway", fallbackMethod="gatewayDown")` — stops hammering a failing
  provider; the fallback returns a *retryable* `unavailable` result.
- `@Retry(name="paymentGateway")` — transient-failure retries with backoff.
- `@Bulkhead(name="paymentGateway")` — caps concurrent calls so a slow gateway can't exhaust threads.

> ⚠️ **Simulated:** currently returns ~85% success at random. The inline comment shows the intended
> real Stripe-style SDK call using `orderId` as the provider `idempotency_key`.

Nested `record GatewayResult(success, retryable, txRef, reason)` with `success` / `declined` /
`unavailable` factories.

### `PaymentRepository.java`
`JpaRepository<Payment, UUID>` with `findByOrderId(orderId)` (used by the idempotency guard).

---

## Idempotency & resilience

- **Idempotency (double-charge protection):** app-level `findByOrderId` early return **+** DB
  `UNIQUE(order_id)` **+** `orderId` as the gateway idempotency key.
- **Compensation role:** this service never compensates directly; it emits `PaymentFailed`, and
  order-service reacts by cancelling the order, which triggers inventory to release the reservation.
- **Resilience4j** (`application.yml`): breaker sliding-window 10 / 50% threshold / 20s open;
  retry 3× / 500ms exponential; bulkhead 25 concurrent calls.

## Database (`db/migration/V1__init_schema.sql`, Flyway)
`payments` (UUID PK, `order_id UNIQUE NOT NULL`, `amount NUMERIC(12,2)`, `status`,
`gateway_transaction_ref`, `failure_reason`, `created_at`). No seed data.
JPA runs `ddl-auto=validate` — Flyway owns the schema.

## Kafka summary
- **Consumes:** `INVENTORY_RESERVED` (group `payment-service`)
- **Produces:** `PAYMENT_PROCESSED`, `PAYMENT_FAILED`

## Config env vars
`DB_HOST` (5434), `DB_USER`, `DB_PASSWORD` (`CHANGE_ME`), `KAFKA_BOOTSTRAP`, `EUREKA_URI`,
`PAYMENT_GATEWAY_API_KEY` (placeholder). Actuator exposes `health,info,prometheus,circuitbreakers`.
