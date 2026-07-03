CREATE TABLE orders (
    id                  UUID PRIMARY KEY,
    customer_id         UUID           NOT NULL,
    status              VARCHAR(32)    NOT NULL,
    total_amount        NUMERIC(12,2)  NOT NULL,
    currency            CHAR(3)        NOT NULL,
    cancellation_reason TEXT,
    created_at          TIMESTAMPTZ    NOT NULL,
    updated_at          TIMESTAMPTZ,
    version             BIGINT         NOT NULL DEFAULT 0
);

CREATE TABLE order_items (
    id         BIGSERIAL PRIMARY KEY,
    order_id   UUID          NOT NULL REFERENCES orders(id),
    product_id UUID          NOT NULL,
    sku        VARCHAR(64)   NOT NULL,
    quantity   INT           NOT NULL CHECK (quantity > 0),
    unit_price NUMERIC(12,2) NOT NULL
);

CREATE TABLE outbox_events (
    id           UUID PRIMARY KEY,
    aggregate_id UUID         NOT NULL,
    topic        VARCHAR(128) NOT NULL,
    event_type   VARCHAR(64)  NOT NULL,
    payload      TEXT         NOT NULL,
    created_at   TIMESTAMPTZ  NOT NULL,
    published_at TIMESTAMPTZ,
    attempts     INT          NOT NULL DEFAULT 0
);
CREATE INDEX idx_outbox_pending ON outbox_events (created_at) WHERE published_at IS NULL;

CREATE TABLE processed_events (
    event_id     UUID PRIMARY KEY,
    processed_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_orders_customer ON orders (customer_id);
CREATE INDEX idx_orders_status   ON orders (status);
