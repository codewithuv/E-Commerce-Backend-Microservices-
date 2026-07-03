# .github

GitHub configuration for the repository. Currently holds the **CI pipeline**.

## `workflows/ci.yml`

A GitHub Actions workflow named **CI**.

- **Triggers:** every push to `main` and every pull request.
- **Runner:** `ubuntu-latest` (Docker is preinstalled, which the Testcontainers integration tests need).

### Job: `build-and-test`
1. `actions/checkout@v4` — check out the repo.
2. `actions/setup-java@v4` — Temurin **JDK 17** with Maven dependency caching.
3. **`mvn -B clean verify`** — builds all modules and runs unit + **Testcontainers** integration tests
   (which spin up real Postgres/Kafka containers).
4. **Build Docker images** — only on `main`: builds the `order-service` image tagged with the commit
   SHA.
   > ⚠️ **Placeholder:** the `docker login` + push to a registry (GHCR/ECR/Docker Hub) step is left as a
   > comment for you to fill in with your registry and credentials.

### To extend
- Add build/push steps for the other services (mirror the order-service block).
- Add a registry login using repository secrets (`secrets.REGISTRY_TOKEN`, etc.).
