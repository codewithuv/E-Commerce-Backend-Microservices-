CREATE TABLE inventory_items (
    product_id         UUID PRIMARY KEY,
    sku                VARCHAR(64) NOT NULL UNIQUE,
    available_quantity INT NOT NULL CHECK (available_quantity >= 0),
    reserved_quantity  INT NOT NULL DEFAULT 0 CHECK (reserved_quantity >= 0),
    version            BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE reservations (
    id         UUID PRIMARY KEY,
    order_id   UUID        NOT NULL,
    product_id UUID        NOT NULL REFERENCES inventory_items(product_id),
    quantity   INT         NOT NULL CHECK (quantity > 0),
    status     VARCHAR(16) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX idx_reservations_order ON reservations (order_id, status);

-- Seed data for local demos
INSERT INTO inventory_items (product_id, sku, available_quantity, reserved_quantity) VALUES
  ('11111111-1111-1111-1111-111111111111', 'SKU-LAPTOP-001', 50, 0),
  ('22222222-2222-2222-2222-222222222222', 'SKU-MOUSE-002', 200, 0),
  ('33333333-3333-3333-3333-333333333333', 'SKU-MONITOR-003', 30, 0);
