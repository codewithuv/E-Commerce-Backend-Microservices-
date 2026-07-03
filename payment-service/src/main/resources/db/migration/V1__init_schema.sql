CREATE TABLE payments (
    id                      UUID PRIMARY KEY,
    order_id                UUID          NOT NULL UNIQUE,
    amount                  NUMERIC(12,2) NOT NULL,
    status                  VARCHAR(16)   NOT NULL,
    gateway_transaction_ref VARCHAR(128),
    failure_reason          TEXT,
    created_at              TIMESTAMPTZ   NOT NULL
);
