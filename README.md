# Event-Driven E-Commerce Backend (Microservices)

Production-style e-commerce order platform built with **Spring Boot 3 microservices** that
communicate asynchronously over **Apache Kafka**, coordinate distributed transactions with the
**Saga pattern (choreography + compensation)**, guarantee reliable messaging with the
**Transactional Outbox pattern**, and stay resilient under failure with **Resilience4j**
circuit breakers, retries and bulkheads — all deployable via **Docker Compose** or **Kubernetes**.

---

## Architecture

```
                        ┌──────────────────┐
   client ──HTTP──────► │   API Gateway    │  JWT auth · Redis rate limiting · circuit breakers
                        │ (Spring Cloud GW)│
                        └───────┬──────────┘
                                │ lb:// via Eureka
        ┌───────────────────────┼────────────────────────┐
        ▼                       ▼                        ▼
┌───────────────┐      ┌────────────────┐       ┌────────────────┐
│ Order Service │      │Inventory Svc   │       │ Payment Svc    │
│  Postgres     │      │  Postgres      │       │  Postgres      │
│  + Outbox     │      │  reservations  │       │  Resilience4j  │
└───────┬───────┘      └───────┬────────┘       └───────┬────────┘
        │                      │                        │
        └───────────► Apache Kafka (events) ◄───────────┘
                               │
                       ┌───────▼────────┐
                       │ Notification   │  email on completed / cancelled
                       │ Service        │
                       └────────────────┘
```

### Saga flow (order fulfilment)

```
Happy path:
  POST /api/orders ──► Order PENDING + OrderCreated (outbox)
  Inventory reserves stock ──► InventoryReserved
  Payment charges card    ──► PaymentProcessed
  Order COMPLETED         ──► OrderCompleted ──► inventory commits stock, email sent

Compensation paths:
  InventoryRejected ──► Order CANCELLED ──► email sent
  PaymentFailed     ──► Order CANCELLED ──► OrderCancelled ──► inventory RELEASES reservation
```

---

## Why the design choices matter (interview talking points)

| Problem | Solution in this repo |
|---|---|
| DB commit succeeds but Kafka publish fails (dual-write) | **Transactional Outbox** — event row committed in the same ACID tx, relay publishes with `SKIP LOCKED`-style batching (`order-service/outbox/`) |
| Kafka is at-least-once → duplicate deliveries | **Idempotent consumers** — `processed_events` ledger keyed by `eventId`; payments also use a DB `UNIQUE(order_id)` so a redelivery can never double-charge |
| Distributed transaction across 3 services with no 2PC | **Choreography saga** with explicit **compensating actions** (release stock reservation on payment failure) |
| One slow downstream (payment provider) takes the platform down | **Resilience4j** circuit breaker + retry + bulkhead around the gateway adapter; gateway-level breakers with fallback endpoints |
| Overselling under concurrent orders | Pessimistic row locks on reserve path + optimistic `@Version` on aggregates; stock modeled as `available` vs `reserved` |
| Event ordering per order | Kafka message key = `orderId` → all saga events for one order share a partition |
| Poison messages block a partition | Exponential-backoff retry then **dead-letter topic** (`*.DLT`) |
| Schema drift between services | Shared `common-events` module, versioned topics (`*.v1`), versioned `EventEnvelope` |

---

## Tech stack

Java 17 · Spring Boot 3.2 · Spring Cloud 2023 (Gateway, Eureka) · Apache Kafka (KRaft) ·
PostgreSQL 16 (database-per-service) · Flyway · Redis · Resilience4j · Docker/Compose ·
Kubernetes (Deployment/Service/HPA) · Testcontainers · GitHub Actions

---

## Running locally

Prereqs: Docker + Docker Compose, JDK 17, Maven.

```bash
# 1. Build all modules
mvn clean package -DskipTests

# 2. Start the full stack (Kafka, 3× Postgres, Redis, all services)
docker compose up --build

# 3. Place an order (through the gateway)
curl -X POST http://localhost:8080/api/orders \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: $(uuidgen)" \
  -d '{
    "customerId": "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa",
    "currency": "USD",
    "lines": [
      { "productId": "11111111-1111-1111-1111-111111111111",
        "sku": "SKU-LAPTOP-001", "quantity": 1, "unitPrice": 999.99 }
    ]
  }'

# 4. Poll the saga state (PENDING → INVENTORY_RESERVED → PAID → COMPLETED)
curl http://localhost:8080/api/orders/<orderId>
```

Useful consoles:
- Kafka UI: http://localhost:8090 (watch saga events flow between topics)
- Eureka:   http://localhost:8761
- Actuator: `/actuator/health`, `/actuator/prometheus`, `/actuator/circuitbreakers`

> Note on local auth: the gateway validates JWTs against `JWT_ISSUER_URI`. For a quick demo
> without an identity provider, comment out the `oauth2` block in
> `api-gateway/src/main/resources/application.yml`, or run Keycloak and point the issuer at it.

## Running tests

```bash
mvn verify   # includes Testcontainers integration tests (needs Docker)
```

## Kubernetes

```bash
kubectl apply -f k8s/namespace.yaml
kubectl -n ecommerce create secret generic order-db-secret --from-literal=password='<STRONG_PASSWORD>'
kubectl apply -f k8s/order-service.yaml
# see k8s/README.md for Kafka (Strimzi) and remaining services
```

---

## Placeholders you must fill before real use

| Location | Placeholder |
|---|---|
| all `application.yml` | `DB_PASSWORD` / `CHANGE_ME` → secret manager or K8s Secrets |
| `api-gateway` | `JWT_ISSUER_URI` → your Keycloak/Auth0/Cognito realm |
| `payment-service` | `PAYMENT_GATEWAY_API_KEY` + real SDK call in `PaymentGatewayClient` |
| `notification-service` | SMTP creds + real customer-email lookup in `EmailSender` |
| `k8s/*.yaml` | `<YOUR_REGISTRY>` image references |
| `OutboxService.currentTraceId()` | wire Micrometer Tracing / OpenTelemetry |

## Roadmap / stretch goals (great follow-up commits)

- Debezium CDC replacing the polling outbox relay
- Avro + Schema Registry instead of JSON envelopes
- Distributed tracing end-to-end (OpenTelemetry → Jaeger/Tempo)
- Grafana dashboards from the Prometheus metrics already exposed
- k6/Gatling load test proving no oversell at 1k concurrent checkouts
- Orchestration-based saga variant (Temporal or a state-machine orchestrator) for comparison

---

## Resume bullet points (adapt to your own words)

- Designed an event-driven e-commerce backend with 4 Spring Boot microservices coordinating
  order fulfilment via choreography-based **Sagas with compensating transactions** over Kafka.
- Eliminated dual-write inconsistency using the **Transactional Outbox pattern** and enforced
  exactly-once processing effects with idempotent consumers and database unique constraints.
- Hardened the platform with **Resilience4j** circuit breakers/retries/bulkheads, Redis-backed
  rate limiting at a Spring Cloud Gateway edge, and per-order Kafka partitioning for event ordering.
- Shipped with database-per-service PostgreSQL, Flyway migrations, **Testcontainers** integration
  tests, Docker Compose, Kubernetes manifests with HPA, and a GitHub Actions CI pipeline.
