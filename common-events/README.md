# common-events

Shared **Kafka contract library** used by every service. It is a plain Java library (no Spring)
so all producers and consumers agree on the exact shape of events and topic names — this is what
prevents *schema drift* between independently deployed microservices.

- **Maven coordinates:** `com.ecommerce:common-events` (parent `ecommerce-microservices:1.0.0`)
- **Package:** `com.ecommerce.common.events`
- **Dependencies:** `jackson-databind`, `jackson-datatype-jsr310` (for `Instant`)
- **Not deployable** — no `application.yml`, no `Dockerfile`. It is packaged as a JAR and pulled in
  by the other modules.

---

## `EventEnvelope<T>`

Every message on Kafka is a JSON-serialized `EventEnvelope<T>`. The envelope carries the cross-cutting
metadata that the reliability patterns depend on, and wraps a strongly-typed `payload`.

| Field | Type | Purpose |
|---|---|---|
| `eventId` | `UUID` | Unique per event. Consumers record it in a `processed_events` ledger to make at-least-once delivery **idempotent** (exactly-once *effect*). |
| `sagaId` | `UUID` | Equals the `orderId`. Correlates every step of one saga instance and is used as the **Kafka message key** so all events for one order land on the same partition (ordering). |
| `eventType` | `String` | Logical event name. |
| `occurredAt` | `Instant` | Event timestamp. |
| `traceId` | `String` | Propagated for distributed tracing. |
| `schemaVersion` | `int` | Envelope/schema version (currently `1`). |
| `payload` | `T` | The typed domain event (records listed below). |

**Factory:** `EventEnvelope.of(UUID sagaId, String eventType, String traceId, T payload)` — generates a
random `eventId`, stamps `occurredAt = Instant.now()`, sets `schemaVersion = 1`.

---

## `KafkaTopics`

`final` class of topic-name constants (private constructor). Naming convention:
`<domain>.<event-type>.v<schema-version>` — the version suffix lets a new event schema roll out on a
new topic without breaking existing consumers.

| Constant | Topic string | Produced by |
|---|---|---|
| `ORDER_CREATED` | `orders.order-created.v1` | order-service |
| `ORDER_COMPLETED` | `orders.order-completed.v1` | order-service |
| `ORDER_CANCELLED` | `orders.order-cancelled.v1` | order-service |
| `INVENTORY_RESERVED` | `inventory.stock-reserved.v1` | inventory-service |
| `INVENTORY_REJECTED` | `inventory.stock-rejected.v1` | inventory-service |
| `INVENTORY_RELEASED` | `inventory.stock-released.v1` | inventory-service |
| `PAYMENT_PROCESSED` | `payments.payment-processed.v1` | payment-service |
| `PAYMENT_FAILED` | `payments.payment-failed.v1` | payment-service |
| `DLT_SUFFIX` | `.DLT` | appended to any topic by the dead-letter error handler for poison messages |

---

## Event payload records

All payloads are immutable Java `record`s:

| Record | Fields |
|---|---|
| `OrderCreatedEvent` | `orderId`, `customerId`, `totalAmount`, `currency`, `List<OrderLine> lines` — nested `OrderLine(productId, sku, quantity, unitPrice)` |
| `OrderCompletedEvent` | `orderId`, `customerId` |
| `OrderCancelledEvent` | `orderId`, `customerId`, `reason` |
| `InventoryReservedEvent` | `orderId`, `reservationId`, `List<ReservedLine> lines` — nested `ReservedLine(productId, quantity)` |
| `InventoryRejectedEvent` | `orderId`, `reason` |
| `InventoryReleasedEvent` | `orderId`, `reservationId` |
| `PaymentProcessedEvent` | `orderId`, `paymentId`, `amount`, `transactionRef` |
| `PaymentFailedEvent` | `orderId`, `reason`, `retryable` |

Together these model the choreographed saga:
`OrderCreated → InventoryReserved | InventoryRejected → PaymentProcessed | PaymentFailed → OrderCompleted | OrderCancelled`,
with `InventoryReleased` as the compensating action.
