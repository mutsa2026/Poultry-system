CREATE SEQUENCE dressed_batch_no_seq;

CREATE TABLE dressed_chicken_batches (
  id             BIGSERIAL PRIMARY KEY,
  batch_no       VARCHAR(24) NOT NULL UNIQUE,
  source_batch_id BIGINT NOT NULL REFERENCES poultry_batches(id),
  processed_date DATE NOT NULL,
  birds_processed INTEGER NOT NULL CHECK (birds_processed > 0),
  dressed_kg     NUMERIC(12,3) NOT NULL CHECK (dressed_kg > 0),
  notes          VARCHAR(255),
  created_by     BIGINT NOT NULL REFERENCES users(id),
  created_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_dressed_batches_source ON dressed_chicken_batches(source_batch_id);

CREATE TABLE dressed_stock_movements (
  id                BIGSERIAL PRIMARY KEY,
  dressed_batch_id  BIGINT NOT NULL REFERENCES dressed_chicken_batches(id),
  movement_type     VARCHAR(20) NOT NULL CHECK (movement_type IN ('PROCESSING','SALE','REVERSAL','ADJUSTMENT')),
  quantity_change   NUMERIC(12,3) NOT NULL CHECK (quantity_change <> 0),
  reference_type    VARCHAR(40),
  reference_id      BIGINT,
  notes             VARCHAR(255),
  created_by        BIGINT NOT NULL REFERENCES users(id),
  created_at        TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_dressed_stock_mov_batch ON dressed_stock_movements(dressed_batch_id);

ALTER TABLE sales
  ADD COLUMN dressed_batch_id BIGINT REFERENCES dressed_chicken_batches(id),
  ADD CONSTRAINT ck_sales_dressed_batch_product
    CHECK (dressed_batch_id IS NULL OR product = 'DRESSED_CHICKEN'),
  ADD CONSTRAINT ck_sales_live_batch_product
    CHECK (product <> 'DRESSED_CHICKEN' OR batch_id IS NULL);
CREATE INDEX idx_sales_dressed_batch ON sales(dressed_batch_id);
