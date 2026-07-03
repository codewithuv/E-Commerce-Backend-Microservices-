# notification-service

A **terminal, read-only saga consumer** that emails the customer when an order reaches a final state.
It is deliberately fire-and-forget: a notification failure must **never** roll back or affect an order.

- **Maven module:** `notification-service` · **Package:** `com.ecommerce.notification`
- **Port:** **8084** · No database
- **Docker:** `eclipse-temurin:17-jre-alpine`, `notification-service-1.0.0.jar`, `EXPOSE 8084`

---

## Package tour

### `NotificationServiceApplication.java`
Plain `@SpringBootApplication` main.

### `NotificationEventHandler.java`
`@Component` consuming the two terminal saga events (group `notification-service`). It deserializes each
JSON message into the correct `EventEnvelope<T>` using
`mapper.getTypeFactory().constructParametricType(...)`.

| Consumes | Method | Email |
|---|---|---|
| `ORDER_COMPLETED` (`orders.order-completed.v1`) | `onOrderCompleted` | "Your order is confirmed" |
| `ORDER_CANCELLED` (`orders.order-cancelled.v1`) | `onOrderCancelled` | "Your order was cancelled" (includes the reason) |

### `EmailSender.java`
`@Component` with `send(customerId, subject, body)`.

> ⚠️ **Placeholder:** currently only **logs** the email (`EMAIL -> customer=… | subject | body`). The
> inline comment describes the intended real flow: resolve the customer's email from a user store, then
> send via `JavaMailSender` / SES / SendGrid.

---

## Config (`application.yml`)
- `server.port: 8084`
- Kafka: `KAFKA_BOOTSTRAP` (default `localhost:9092`), consumer group `notification-service`,
  `auto-offset-reset: earliest`, `StringDeserializer` key/value.
- Mail (**placeholders**): `SMTP_HOST` (default `smtp.example.com`), port 587, `SMTP_USER`,
  `SMTP_PASSWORD`.
- `EUREKA_URI` (default `http://localhost:8761/eureka`).

## Kafka summary
- **Consumes:** `ORDER_COMPLETED`, `ORDER_CANCELLED`
- **Produces:** nothing (leaf of the saga)
