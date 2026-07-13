CREATE TABLE "user"
(
    id                BIGSERIAL PRIMARY KEY,
    google_subject_id TEXT UNIQUE, -- NULL for the seeded admin-only account
    email             TEXT        NOT NULL UNIQUE,
    display_name      TEXT        NOT NULL,
    role              TEXT        NOT NULL CHECK (role IN ('USER', 'ADMIN')),
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE admin_credential
(
    user_id       BIGINT PRIMARY KEY REFERENCES "user" (id),
    password_hash TEXT        NOT NULL,
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE auction
(
    id               BIGSERIAL PRIMARY KEY,
    item_id          TEXT           NOT NULL,         -- unique item ID in the source system
    title            TEXT,                            -- filled in by admin while editing the draft
    description      TEXT           NOT NULL,
    category         TEXT           NOT NULL,
    auction_type     TEXT CHECK (auction_type IN ('FIRST_PRICE', 'SECOND_PRICE')), -- filled in by admin while editing the draft
    lifecycle_status TEXT           NOT NULL CHECK (lifecycle_status IN ('DRAFT', 'ACTIVE', 'ARCHIVED')),
    start_price      NUMERIC(12, 2) NOT NULL,
    current_value    NUMERIC(12, 2) NOT NULL,
    currency         TEXT           NOT NULL DEFAULT 'EUR',
    starts_at        TIMESTAMPTZ    NOT NULL,
    ends_at          TIMESTAMPTZ,                     -- filled in by admin while editing the draft
    comment          TEXT,
    serial_number    TEXT,
    created_at       TIMESTAMPTZ    NOT NULL DEFAULT now(),
    CHECK (ends_at IS NULL OR ends_at > starts_at),
    UNIQUE (item_id) -- ingestion idempotency key
);
CREATE INDEX idx_auction_lifecycle_ends_at ON auction (lifecycle_status, ends_at);

CREATE TABLE bid
(
    auction_id        BIGINT      NOT NULL REFERENCES auction (id) ON DELETE CASCADE,
    user_id           BIGINT      NOT NULL REFERENCES "user" (id),
    amount            NUMERIC(12, 2), -- nullable, preserved even when withdrawn/moderated
    is_withdrawn      BOOLEAN     NOT NULL DEFAULT FALSE,
    moderated_by      BIGINT REFERENCES "user" (id),
    moderation_reason TEXT,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (auction_id, user_id)
);
CREATE INDEX idx_bid_user_id ON bid (user_id);

CREATE TABLE auction_result
(
    id                  BIGSERIAL PRIMARY KEY,
    auction_id          BIGINT      NOT NULL UNIQUE REFERENCES auction (id) ON DELETE CASCADE,
    result_status       TEXT        NOT NULL CHECK (result_status IN ('SOLD', 'UNSOLD', 'VOIDED')),
    winner_user_id      BIGINT REFERENCES "user" (id),
    winning_price       NUMERIC(12, 2),
    finalized_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    paid_at             TIMESTAMPTZ,
    invalidated_at      TIMESTAMPTZ,
    invalidated_by      BIGINT REFERENCES "user" (id),
    invalidation_reason TEXT,
    version             BIGINT      NOT NULL DEFAULT 0 -- optimistic lock, @Version
);

CREATE TABLE bid_event
(
    id              BIGSERIAL PRIMARY KEY,
    auction_id      BIGINT      NOT NULL REFERENCES auction (id) ON DELETE CASCADE,
    user_id         BIGINT      NOT NULL REFERENCES "user" (id),
    event_type      TEXT        NOT NULL CHECK (event_type IN ('PLACED', 'CHANGED', 'WITHDRAWN', 'MODERATED')),
    amount_snapshot NUMERIC(12, 2),
    actor_user_id   BIGINT      NOT NULL REFERENCES "user" (id), -- self, or the admin for MODERATED
    reason          TEXT,
    occurred_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);
-- Append-only: no update/delete path anywhere in the app.

CREATE TABLE shedlock
(
    name       VARCHAR(64) PRIMARY KEY,
    lock_until TIMESTAMP    NOT NULL,
    locked_at  TIMESTAMP    NOT NULL,
    locked_by  VARCHAR(255) NOT NULL
);
