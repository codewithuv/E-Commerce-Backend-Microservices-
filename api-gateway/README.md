# api-gateway

The single **edge entry point** for all clients, built on **Spring Cloud Gateway** (reactive).
It handles JWT authentication, distributed rate limiting, circuit breaking with fallbacks, and
routes requests to backend services discovered through Eureka.

- **Maven module:** `api-gateway`  ·  **Package:** `com.ecommerce.gateway`
- **Port:** **8080**  ·  **Docker:** `eclipse-temurin:17-jre-alpine`, `api-gateway-1.0.0.jar`, `EXPOSE 8080`
- **Dependencies:** `spring-cloud-starter-gateway`, `netflix-eureka-client`,
  `circuitbreaker-reactor-resilience4j`, `data-redis-reactive` (rate limiting),
  `oauth2-resource-server` (JWT), `actuator`

---

## Files

### `ApiGatewayApplication.java`
Plain `@SpringBootApplication` main.

### `RateLimiterConfig.java`
Defines the `principalOrIpKeyResolver` bean (`KeyResolver`). It decides *what* the rate limiter counts
against: the authenticated principal name → else the caller's remote IP → else `"anonymous"`. Because
the limiter state lives in **Redis**, limits are enforced consistently across every gateway replica.

### `FallbackController.java`
`@RestController` providing the circuit-breaker fallback endpoints. When a downstream breaker is open,
the route forwards here and the client gets **HTTP 503** with a friendly JSON body instead of a hang:
- `GET /fallback/orders` → `{status: DEGRADED, message: "Order service is temporarily unavailable..."}`
- `GET /fallback/inventory` → similar for inventory.

### `application.yml`
The heart of the gateway. Key sections:

**Global default filter** — every route gets a Redis `RequestRateLimiter` using
`#{@principalOrIpKeyResolver}`, `replenishRate: 20` tokens/sec, `burstCapacity: 40`.

**Routes**

| id | predicate | target (`lb://` = Eureka load-balanced) | filters |
|---|---|---|---|
| `order-service` | `Path=/api/orders/**` | `lb://order-service` | `CircuitBreaker(orderCircuitBreaker)` → `forward:/fallback/orders`; `Retry` (2×, on `BAD_GATEWAY`/`GATEWAY_TIMEOUT`, GET only) |
| `inventory-service` | `Path=/api/inventory/**` | `lb://inventory-service` | `CircuitBreaker(inventoryCircuitBreaker)` → `forward:/fallback/inventory` |

**Security** — OAuth2 resource server validates JWTs against
`JWT_ISSUER_URI` (default `http://localhost:9000/realms/ecommerce`, a placeholder for
Keycloak/Auth0/Cognito).

**Resilience4j** — circuit breakers: `COUNT_BASED`, slidingWindow 20, failureRateThreshold 50%,
`waitDurationInOpenState` 15s, 5 permitted calls in half-open; `timelimiter` 3s.

**Other** — `REDIS_HOST` (default `localhost`:6379), `EUREKA_URI`, actuator exposes
`health,info,prometheus,circuitbreakers`.

---

## ⚠️ Running locally without an identity provider

The gateway rejects unauthenticated calls because of the `oauth2` resource-server config. For a quick
demo with no Keycloak/Auth0, **comment out the `oauth2` block** in `application.yml` (or stand up an
IdP and point `JWT_ISSUER_URI` at it). See the placeholders table in the root `README.md`.
