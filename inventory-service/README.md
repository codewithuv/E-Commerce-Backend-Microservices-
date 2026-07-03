# inventory-service

A **saga participant** that manages stock. It reserves stock when an order is created, **releases**
it (compensation) if the order is cancelled, and **commits** it (hard decrement) when the order
completes. It is purely event-driven — no public REST API.

- **Maven module:** `inventory-service` · **Package:** `com.ecommerce.inventory`
- **Port:** **8082** · DB: PostgreSQL `inventory_db` (port 5433)
- **Docker:** `eclipse-temurin:17-jre-alpine`, `inventory-service-1.0.0.jar`, `EXPOSE 8082`

---

## The reservation model (two-phase stock)

Stock is modelled as `availableQuantity` vs `reservedQuantity` so that concurrent orders never
oversell. A reservation is a **soft hold** first, then either released or committed:

```
OrderCreated   → reserve:  reservedQuantity += qty          (HELD)
OrderCancelled → release:  reservedQuantity -= qty          (RELEASED)  ← compensation
OrderCompleted → commit:   reserved -= qty; available -= qty (COMMITTED)
```

---

## Package tour

### `InventoryServiceApplication.java`
Standard `@SpringBootApplication` main.

### `InventoryEventHandler.java` — core logic
Three `@KafkaListener` + `@Transactional` methods (group `inventory-service`):

| Consumes | Method | Behaviour | Emits |
|---|---|---|---|
| `ORDER_CREATED` | `onOrderCreated` | **All-or-nothing**: for each line, `findForUpdate` (pessimistic lock) then `item.reserve(qty)` + save a `Reservation`. Any line failing rolls the whole tx back. | `INVENTORY_RESERVED` on success; `INVENTORY_REJECTED` on `IllegalStateException` (unknown product / insufficient stock) |
| `ORDER_CANCELLED` | `onOrderCancelled` | Compensation: load `HELD` reservations, `item.release(qty)` + mark `RELEASED`. | `INVENTORY_RELEASED` (if any were held) |
| `ORDER_COMPLETED` | `onOrderCompleted` | Load `HELD` reservations, `item.commitReservation(qty)` + mark `COMMITTED`. | — |

`publish(...)` wraps each payload in an `EventEnvelope` and sends it keyed by `sagaId`, propagating
the parent `sagaId`/`traceId`.

### `InventoryItem.java` (`inventory_items`)
Entity keyed by `productId`, with `sku`, `availableQuantity`, `reservedQuantity`, and an `@Version`
optimistic lock. Business methods: `canReserve`, `reserve` (throws if insufficient), `release`
(compensation), `commitReservation` (decrement both counters).

### `Reservation.java` (`reservations`)
Tracks exactly what was held per order (`orderId`, `productId`, `quantity`, `status`) so compensation
releases the exact quantities. Status enum `HELD → RELEASED | COMMITTED`.

### Repositories
- **`InventoryRepository.findForUpdate(productId)`** — `@Lock(PESSIMISTIC_WRITE)`; a row lock on the
  reserve hot path that, together with `@Version` and DB `CHECK (… >= 0)` constraints, prevents
  overselling under concurrency.
- **`ReservationRepository.findByOrderIdAndStatus(orderId, status)`**.

---

## Idempotency note

Unlike order-service and payment-service, this service has **no `processed_events` ledger**. Cancel and
complete are naturally idempotent (they filter on `HELD`, so a redelivery finds nothing to do), but a
**duplicate `ORDER_CREATED` would reserve twice**. This is a known gap worth hardening (add a
processed-events table or a `UNIQUE(order_id, product_id)` on reservations).

## Database (`db/migration/V1__init_schema.sql`, Flyway)
- `inventory_items` (PK `product_id`, unique `sku`, quantities with `CHECK >= 0`, `version`)
- `reservations` (FK → inventory_items, `CHECK quantity > 0`, index on `(order_id, status)`)
- **Seed data:** SKU-LAPTOP-001 (50), SKU-MOUSE-002 (200), SKU-MONITOR-003 (30)

JPA runs `ddl-auto=validate` — Flyway owns the schema.

## Kafka summary
- **Consumes:** `ORDER_CREATED`, `ORDER_CANCELLED`, `ORDER_COMPLETED`
- **Produces:** `INVENTORY_RESERVED`, `INVENTORY_REJECTED`, `INVENTORY_RELEASED`

## Config env vars
`DB_HOST` (5433), `DB_USER`, `DB_PASSWORD` (`CHANGE_ME`), `KAFKA_BOOTSTRAP`, `EUREKA_URI`.
Actuator exposes `health,info,prometheus`.
