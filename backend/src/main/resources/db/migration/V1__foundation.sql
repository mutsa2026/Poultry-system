CREATE TABLE users (
  id            BIGSERIAL PRIMARY KEY,
  username      VARCHAR(60)  NOT NULL UNIQUE,
  password_hash VARCHAR(100) NOT NULL,
  full_name     VARCHAR(120) NOT NULL,
  role          VARCHAR(20)  NOT NULL CHECK (role IN ('OWNER','WORKER')),
  active        BOOLEAN      NOT NULL DEFAULT TRUE,
  created_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE audit_logs (
  id          BIGSERIAL PRIMARY KEY,
  actor_id    BIGINT REFERENCES users(id),
  action      VARCHAR(60)  NOT NULL,
  entity_type VARCHAR(60)  NOT NULL,
  entity_id   BIGINT,
  details     TEXT,
  created_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE suppliers (
  id      BIGSERIAL PRIMARY KEY,
  name    VARCHAR(120) NOT NULL,
  phone   VARCHAR(40),
  address VARCHAR(255),
  active  BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE poultry_batches (
  id               BIGSERIAL PRIMARY KEY,
  batch_code       VARCHAR(40) NOT NULL UNIQUE,
  supplier_id      BIGINT REFERENCES suppliers(id),
  arrival_date     DATE NOT NULL,
  breed            VARCHAR(60),
  initial_quantity INTEGER NOT NULL CHECK (initial_quantity > 0),
  purchase_cost    NUMERIC(14,2) NOT NULL CHECK (purchase_cost >= 0),
  status           VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE','CLOSED')),
  created_by       BIGINT NOT NULL REFERENCES users(id),
  created_at       TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Ledger: current live stock of a batch = SUM(quantity_change)
CREATE TABLE stock_movements (
  id              BIGSERIAL PRIMARY KEY,
  batch_id        BIGINT NOT NULL REFERENCES poultry_batches(id),
  movement_type   VARCHAR(20) NOT NULL CHECK (movement_type IN
                   ('OPENING','SALE','MORTALITY','CULL','MISSING','PROCESSING','ADJUSTMENT','REVERSAL')),
  quantity_change INTEGER NOT NULL CHECK (quantity_change <> 0),
  reference_type  VARCHAR(40),
  reference_id    BIGINT,
  notes           VARCHAR(255),
  created_by      BIGINT NOT NULL REFERENCES users(id),
  created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_stock_mov_batch ON stock_movements(batch_id);
-- exactly one opening movement per batch
CREATE UNIQUE INDEX uq_one_opening_per_batch ON stock_movements(batch_id) WHERE movement_type = 'OPENING';

CREATE TABLE mortality_events (
  id           BIGSERIAL PRIMARY KEY,
  batch_id     BIGINT NOT NULL REFERENCES poultry_batches(id),
  event_date   DATE NOT NULL,
  quantity     INTEGER NOT NULL CHECK (quantity > 0),
  cause        VARCHAR(120),
  notes        VARCHAR(255),
  movement_id  BIGINT NOT NULL UNIQUE REFERENCES stock_movements(id),
  reported_by  BIGINT NOT NULL REFERENCES users(id),
  created_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_mortality_batch_date ON mortality_events(batch_id, event_date);
