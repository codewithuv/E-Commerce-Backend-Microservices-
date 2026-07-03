# Kubernetes Manifests

`order-service.yaml` is the reference manifest (Deployment + Service + HPA with
liveness/readiness probes and resource limits). Duplicate the same pattern for
`inventory-service`, `payment-service`, `notification-service`, `api-gateway`
and `discovery-server`, changing image, port and env vars.

For infrastructure, use operators/Helm rather than hand-rolled manifests:

- Kafka:    Strimzi operator  (`helm install kafka strimzi/strimzi-kafka-operator`)
- Postgres: CloudNativePG or Bitnami chart
- Redis:    Bitnami chart

Secrets shown as PLACEHOLDER must be created first, e.g.:

    kubectl -n ecommerce create secret generic order-db-secret --from-literal=password='<STRONG_PASSWORD>'
