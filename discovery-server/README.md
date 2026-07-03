# discovery-server

**Netflix Eureka** service registry. Every other service registers here on startup and looks up
peers by logical name (e.g. `lb://order-service`) instead of hard-coded host/ports, which is what
lets the API gateway client-side load-balance across replicas.

- **Maven module:** `discovery-server`
- **Dependency:** `spring-cloud-starter-netflix-eureka-server`
- **Port:** **8761** (dashboard at http://localhost:8761)
- **Docker:** `eclipse-temurin:17-jre-alpine`, runs `discovery-server-1.0.0.jar`, `EXPOSE 8761`

---

## Files

### `DiscoveryServerApplication.java`
`@SpringBootApplication` + `@EnableEurekaServer`. Standard `main`.

### `application.yml`
| Setting | Value | Why |
|---|---|---|
| `server.port` | `8761` | Standard Eureka port other services default to. |
| `spring.application.name` | `discovery-server` | |
| `eureka.client.register-with-eureka` | `false` | This *is* the server — it doesn't register with itself. |
| `eureka.client.fetch-registry` | `false` | Standalone server; nothing to fetch. |
| `eureka.server.enable-self-preservation` | `false` | Disabled for local dev so dead instances expire fast. **Enable in production** (see inline comment). |

---

## Role in the system

```
order/inventory/payment/notification services ──register──►  Eureka (8761)
api-gateway  ──lookup lb://order-service──►  Eureka  ──►  routes to a live replica
```

Bring this up **first** — `docker-compose.yml` makes the gateway and all services `depends_on` it.
The `EUREKA_URI` env var on every other service points back here
(`http://discovery-server:8761/eureka`).
