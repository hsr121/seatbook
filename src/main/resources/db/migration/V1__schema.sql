CREATE TABLE shows (
  id             VARCHAR(36)   NOT NULL PRIMARY KEY,
  name           NVARCHAR(200) NOT NULL,
  price_paise    BIGINT        NOT NULL CHECK (price_paise >= 0),
  per_user_limit INT           NOT NULL CHECK (per_user_limit > 0),
  total_seats    INT           NOT NULL CHECK (total_seats > 0),
  created_at     DATETIME2     NOT NULL DEFAULT SYSUTCDATETIME()
);

-- One row per seat. The atomic decision is a conditional UPDATE guarded on status='available'.
CREATE TABLE seats (
  show_id        VARCHAR(36)  NOT NULL,
  seat_label     VARCHAR(32) COLLATE Latin1_General_100_BIN2 NOT NULL,   -- exact, case-sensitive match
  status         VARCHAR(12)  NOT NULL DEFAULT 'available'
                 CHECK (status IN ('available','held','confirmed')),
  reservation_id VARCHAR(36)  NULL,
  user_id        VARCHAR(128) NULL,
  CONSTRAINT pk_seats PRIMARY KEY CLUSTERED (show_id, seat_label),
  CONSTRAINT fk_seats_show FOREIGN KEY (show_id) REFERENCES shows(id)
);
CREATE INDEX ix_seats_reservation ON seats(reservation_id) WHERE reservation_id IS NOT NULL;

-- Idempotency lives here: UNIQUE(user_id, idempotency_key) makes "reserve twice" impossible.
CREATE TABLE reservations (
  id              VARCHAR(36)   NOT NULL PRIMARY KEY,
  show_id         VARCHAR(36)   NOT NULL,
  user_id         VARCHAR(128)  NOT NULL,
  idempotency_key VARCHAR(128)  NOT NULL,
  request_hash    CHAR(64)      NOT NULL,
  seats           VARCHAR(1024) NOT NULL,       -- sorted, comma separated
  amount_paise    BIGINT        NOT NULL,
  status          VARCHAR(12)   NOT NULL CHECK (status IN ('confirmed','cancelled')),
  created_at      DATETIME2     NOT NULL DEFAULT SYSUTCDATETIME(),
  CONSTRAINT uq_reservation_idem UNIQUE (user_id, idempotency_key),
  CONSTRAINT fk_res_show FOREIGN KEY (show_id) REFERENCES shows(id)
);

-- Per-user limit counter; bumped with a guarded MERGE in the same transaction as the seat claim.
CREATE TABLE user_show_quota (
  show_id      VARCHAR(36)   NOT NULL,
  user_id      VARCHAR(128)  NOT NULL,
  active_seats INT           NOT NULL CHECK (active_seats >= 0),
  CONSTRAINT pk_quota PRIMARY KEY (show_id, user_id)
);
