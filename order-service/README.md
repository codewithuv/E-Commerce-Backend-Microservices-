# order-service

The **saga initiator / coordinator** for order fulfilment. It owns the `Order` aggregate and its
state machine, exposes the public REST API, and demonstrates two key reliability patterns:
the **Transactional Outbox** (no dual-write) and **idempotent consumers** (exactly-once effect over
Kafka's at-least-once delivery).

- **Maven module:** `order-service` · **Package:** `com.ecommerce.order`
- **Port:** **8081** · DB: PostgreSQL `orders_db` (port 5432)
- **Docker:** multi-stage (`maven:3.9-eclipse-temurin-17` build → `eclipse-temurin:17-jre-alpine`),
  `-XX:MaxRAMPercentage=75`, healthcheck on `/actuator/health`

---

## REST API

| Method | Path | Behaviour |
|---|---|---|
| `POST` | `/api/orders` | Creates an order. Optional `Idempotency-Key` header. Returns **202 Accepted**, `Location: /api/orders/{id}`, body `{orderId, status}`. Fulfilment is asynchronous — clients poll. |
| `GET` | `/api/orders/{id}` | Returns the order, or 404. |

Fulfilment is async, so a POST returns immediately with `PENDING`; the saga advances it in the
background (`PENDING → INVENTORY_RESERVED → PAID → COMPLETED`, or `→ CANCELLED`).

---

## Package tour

### `OrderServiceApplication.java`
`@SpringBootApplication` + `@EnableScheduling` (the scheduling is required by the outbox relay's
`@Scheduled` poll).

### `domain/` — the aggregate
- **`Order.java`** — aggregate root (`orders` table). Holds status, `totalAmount`, `currency`, an
  `@Version` optimistic lock, and its `OrderItem`s (cascade, eager). Factory `create(...)` builds a
  `PENDING` order and computes the total. The **state-machine methods** enforce valid transitions and
  throw `IllegalStateException` otherwise: `markInventoryReserved()` (PENDING→INVENTORY_RESERVED),
  `markPaid()` (→PAID), `complete()` (→COMPLETED), `cancel(reason)` (any non-completed → CANCELLED).
- **`OrderItem.java`** — line item (`order_items`), `@ManyToOne` back to the order.
- **`OrderStatus.java`** — enum `PENDING, INVENTORY_RESERVED, PAID, COMPLETED, CANCELLED`.
- **`OrderRepository.java`** — `JpaRepository<Order, UUID>`.

### `outbox/` — Transactional Outbox pattern
Solves the dual-write problem: you cannot atomically commit to Postgres **and** publish to Kafka.
Instead the event is written to an `outbox_events` row **in the same DB transaction** as the
aggregate change; a relay publishes it afterwards.
- **`OutboxEvent.java`** — row: `aggregateId` (= orderId, used as Kafka key), `topic`, `eventType`,
  `payload` (serialized `EventEnvelope` JSON), `publishedAt` (null = pending), `attempts`.
- **`OutboxService.append(...)`** — wraps the payload in an `EventEnvelope` and saves the row. Called
  from inside the domain transaction. (`currentTraceId()` is a placeholder — wire Micrometer/OTel.)
- **`OutboxRepository.findBatchToPublish()`** — pessimistic-locked batch query
  (`publishedAt IS NULL AND attempts < 10 ORDER BY createdAt LIMIT 50`), SKIP-LOCKED-style so multiple
  relay instances don't double-publish.
- **`OutboxRelay.java`** — `@Scheduled` (every 500ms) poller: publishes each pending row to Kafka
  **keyed by `aggregateId`** (per-order ordering), marks it published, retries failures up to 10
  attempts. Swappable for Debezium CDC.

### `messaging/` — idempotency ledger
- **`ProcessedEvent.java`** (`processed_events`, PK `eventId`) + repository. Each saga listener records
  the `eventId` in the *same transaction* as the state change, so a Kafka redelivery becomes a no-op.

### `saga/OrderSagaHandler.java` — choreography consumer
`@KafkaListener` methods (group `order-service`), each `@Transactional` and guarded by
`alreadyProcessed(eventId)`:

| Consumes | Action | Emits (via outbox) |
|---|---|---|
| `INVENTORY_RESERVED` | `order.markInventoryReserved()` | — (payment-service listens to the same topic) |
| `PAYMENT_PROCESSED` | `markPaid()` + `complete()` | `OrderCompleted` → `ORDER_COMPLETED` |
| `INVENTORY_REJECTED` | `cancel("Inventory rejected: …")` | `OrderCancelled` → `ORDER_CANCELLED` |
| `PAYMENT_FAILED` | `cancel("Payment failed: …")` | `OrderCancelled` → `ORDER_CANCELLED` (triggers inventory release) |

### `api/`
- **`OrderController.java`** — the endpoints above.
- **`CreateOrderRequest.java`** — validated request record (`customerId`, `currency` 3-char,
  non-empty `lines` with quantity 1–100 and positive `unitPrice`).
- **`OrderApplicationService.placeOrder(...)`** — `@Transactional`: `Order.create` → save →
  `outbox.append(ORDER_CREATED, OrderCreatedEvent)`. Order + outbox row commit atomically.
- **`GlobalExceptionHandler.java`** — RFC 7807 `ProblemDetail`: validation→400, not-found→404,
  invalid saga transition (`IllegalStateException`)→409.

### `config/KafkaConfig.java`
- `DefaultErrorHandler` + `DeadLetterPublishingRecoverer` with exponential backoff (1s→30s): poison
  messages go to `<topic>.DLT` after retries.
- Declares topics `ORDER_CREATED`, `ORDER_COMPLETED`, `ORDER_CANCELLED` (6 partitions, 1 replica).

---

## Database (`db/migration/V1__init_schema.sql`, Flyway)
- `orders` (UUID PK, `version` optimistic lock, indexes on customer & status)
- `order_items` (BIGSERIAL PK, FK → orders)
- `outbox_events` (partial index on pending rows)
- `processed_events` (PK `event_id`)

JPA runs with `ddl-auto=validate` — **Flyway owns the schema.**

## Kafka summary
- **Produces** (via outbox): `ORDER_CREATED`, `ORDER_COMPLETED`, `ORDER_CANCELLED`
- **Consumes** (saga, group `order-service`): `INVENTORY_RESERVED`, `PAYMENT_PROCESSED`,
  `INVENTORY_REJECTED`, `PAYMENT_FAILED`
- Producer `enable.idempotence=true`, `acks=all`; consumer manual ack (`ack-mode=record`).

## Tests
`OrderSagaIntegrationTest` — `@SpringBootTest` with **Testcontainers** (real Postgres + Kafka). Verifies
that placing an order persists the aggregate and the outbox row **atomically** (status `PENDING`,
correct total). Requires Docker to run.

## Config env vars
`DB_HOST` (5432), `DB_USER`, `DB_PASSWORD` (`CHANGE_ME` placeholder), `KAFKA_BOOTSTRAP`, `EUREKA_URI`,
`outbox.relay.poll-interval-ms` (500).
