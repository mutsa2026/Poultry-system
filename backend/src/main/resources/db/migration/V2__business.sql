ALTER TABLE users
  ADD COLUMN perm_pay      BOOLEAN NOT NULL DEFAULT FALSE,
  ADD COLUMN perm_discount BOOLEAN NOT NULL DEFAULT FALSE,
  ADD COLUMN perm_profit   BOOLEAN NOT NULL DEFAULT FALSE;

CREATE SEQUENCE sale_no_seq;
CREATE SEQUENCE receipt_no_seq;

CREATE TABLE customers (
  id           BIGSERIAL PRIMARY KEY,
  name         VARCHAR(120) NOT NULL,
  phone        VARCHAR(40),
  address      VARCHAR(255),
  lat          NUMERIC(9,6),
  lon          NUMERIC(9,6),
  credit_limit NUMERIC(14,2) NOT NULL DEFAULT 0 CHECK (credit_limit >= 0),
  terms_days   INTEGER NOT NULL DEFAULT 14 CHECK (terms_days >= 0),
  active       BOOLEAN NOT NULL DEFAULT TRUE,
  created_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE sales (
  id           BIGSERIAL PRIMARY KEY,
  sale_no      VARCHAR(20) NOT NULL UNIQUE,
  customer_id  BIGINT NOT NULL REFERENCES customers(id),
  batch_id     BIGINT REFERENCES poultry_batches(id),
  product      VARCHAR(20) NOT NULL CHECK (product IN ('LIVE_BROILER','DRESSED_CHICKEN')),
  quantity     NUMERIC(12,2) NOT NULL CHECK (quantity > 0),
  unit_price   NUMERIC(12,2) NOT NULL CHECK (unit_price >= 0),
  discount_pct NUMERIC(5,2)  NOT NULL DEFAULT 0 CHECK (discount_pct BETWEEN 0 AND 100),
  total        NUMERIC(14,2) NOT NULL CHECK (total >= 0),
  sale_date    DATE NOT NULL,
  due_date     DATE NOT NULL,
  status       VARCHAR(12) NOT NULL DEFAULT 'OPEN' CHECK (status IN ('OPEN','CANCELLED')),
  created_by   BIGINT NOT NULL REFERENCES users(id),
  created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
  CHECK (product <> 'LIVE_BROILER' OR batch_id IS NOT NULL)
);
CREATE INDEX idx_sales_customer ON sales(customer_id);
CREATE INDEX idx_sales_date ON sales(sale_date);

CREATE TABLE payments (
  id          BIGSERIAL PRIMARY KEY,
  sale_id     BIGINT NOT NULL REFERENCES sales(id),
  receipt_no  VARCHAR(20) NOT NULL,
  amount      NUMERIC(14,2) NOT NULL CHECK (amount > 0),
  method      VARCHAR(20) NOT NULL CHECK (method IN ('CASH','BANK_TRANSFER','MOBILE_MONEY')),
  reference   VARCHAR(80),
  received_by BIGINT NOT NULL REFERENCES users(id),
  created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_payments_sale ON payments(sale_id);

CREATE TABLE feed_types (
  id            BIGSERIAL PRIMARY KEY,
  name          VARCHAR(80) NOT NULL UNIQUE,
  unit          VARCHAR(20) NOT NULL,
  reorder_level NUMERIC(12,2) NOT NULL CHECK (reorder_level >= 0)
);
INSERT INTO feed_types(name, unit, reorder_level) VALUES ('Stockfeed 50 kg bag', 'bag', 10);

CREATE TABLE feed_movements (
  id              BIGSERIAL PRIMARY KEY,
  feed_type_id    BIGINT NOT NULL REFERENCES feed_types(id),
  quantity_change NUMERIC(12,2) NOT NULL CHECK (quantity_change <> 0),
  kind            VARCHAR(15) NOT NULL CHECK (kind IN ('RECEIPT','CONSUMPTION','ADJUSTMENT')),
  batch_id        BIGINT REFERENCES poultry_batches(id),
  notes           VARCHAR(255),
  created_by      BIGINT NOT NULL REFERENCES users(id),
  created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE purchases (
  id            BIGSERIAL PRIMARY KEY,
  supplier_id   BIGINT NOT NULL REFERENCES suppliers(id),
  item          VARCHAR(12) NOT NULL CHECK (item IN ('CHICKS','FEED','MEDICINE','PACKAGING','OTHER')),
  description   VARCHAR(255),
  quantity      NUMERIC(12,2) NOT NULL CHECK (quantity > 0),
  total_cost    NUMERIC(14,2) NOT NULL CHECK (total_cost > 0),
  purchase_date DATE NOT NULL,
  due_date      DATE NOT NULL,
  created_by    BIGINT NOT NULL REFERENCES users(id),
  created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE supplier_payments (
  id          BIGSERIAL PRIMARY KEY,
  supplier_id BIGINT NOT NULL REFERENCES suppliers(id),
  amount      NUMERIC(14,2) NOT NULL CHECK (amount > 0),
  paid_date   DATE NOT NULL,
  created_by  BIGINT NOT NULL REFERENCES users(id),
  created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE expenses (
  id           BIGSERIAL PRIMARY KEY,
  category     VARCHAR(40) NOT NULL,
  description  VARCHAR(255),
  amount       NUMERIC(14,2) NOT NULL CHECK (amount > 0),
  expense_date DATE NOT NULL,
  batch_id     BIGINT REFERENCES poultry_batches(id),
  created_by   BIGINT NOT NULL REFERENCES users(id),
  created_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE vaccination_records (
  id         BIGSERIAL PRIMARY KEY,
  batch_id   BIGINT NOT NULL REFERENCES poultry_batches(id),
  day_number INTEGER NOT NULL CHECK (day_number > 0),
  vaccine    VARCHAR(120) NOT NULL,
  done_by    BIGINT NOT NULL REFERENCES users(id),
  done_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE (batch_id, day_number)
);

CREATE TABLE visitor_log (
  id               BIGSERIAL PRIMARY KEY,
  visitor_name     VARCHAR(120) NOT NULL,
  purpose          VARCHAR(30) NOT NULL,
  vehicle_plate    VARCHAR(20),
  footbath_used    BOOLEAN NOT NULL,
  poultry_contact  BOOLEAN NOT NULL,
  logged_by        BIGINT NOT NULL REFERENCES users(id),
  logged_at        TIMESTAMPTZ NOT NULL DEFAULT now()
);
