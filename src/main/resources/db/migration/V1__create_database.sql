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

-- Append-only auction log: every change (ingest, activate, extend, cancel, finalize, ...) INSERTs a new
-- row; rows are never mutated. The current state of a logical auction is its latest (MAX(id)) row.
-- auction_ref is the stable logical id shared across an auction's versions; bid.auction_id and
-- auction_result.auction_id reference it (no DB FK — the target is non-unique here — so app code enforces it).
CREATE SEQUENCE auction_ref_seq;
CREATE TABLE auction
(
    id               BIGSERIAL PRIMARY KEY,                    -- version/ordering token; latest = MAX(id) per auction_ref
    auction_ref      BIGINT         NOT NULL,                  -- stable logical auction id (from auction_ref_seq)
    item_id          TEXT           NOT NULL,                  -- item ID in the source system (ingestion idempotency key)
    title            TEXT,                            -- filled in by admin while editing the draft
    description      TEXT           NOT NULL,
    category         TEXT           NOT NULL,
    auction_type     TEXT CHECK (auction_type IN ('FIRST_PRICE', 'SECOND_PRICE')), -- filled in by admin while editing the draft
    lifecycle_status TEXT           NOT NULL CHECK (lifecycle_status IN ('DRAFT', 'ACTIVE', 'SOLD', 'UNSOLD', 'CANCELLED', 'ARCHIVED')),
    start_price      NUMERIC(12, 2) NOT NULL,
    current_value    NUMERIC(12, 2) NOT NULL,
    currency         TEXT           NOT NULL DEFAULT 'EUR',
    starts_at        TIMESTAMPTZ,                     -- set by admin on activation; defaults to activation time
    ends_at          TIMESTAMPTZ,                     -- filled in by admin while editing the draft
    comment          TEXT,
    serial_number    TEXT,
    created_at       TIMESTAMPTZ    NOT NULL DEFAULT now(),    -- when this version row was written
    CHECK (ends_at IS NULL OR ends_at > starts_at)
);
-- Latest-version-per-auction lookups (findCurrentByRef and the MAX(id) current-state subqueries).
CREATE INDEX idx_auction_current ON auction (auction_ref, id DESC);
-- Ingestion idempotency: latest version for a given source item_id.
CREATE INDEX idx_auction_item ON auction (item_id, id DESC);
-- Narrows the finalization scan before the current-state collapse.
CREATE INDEX idx_auction_lifecycle_ends_at ON auction (lifecycle_status, ends_at);

-- Append-only bid log: place/update/withdraw/moderate each INSERT a new row; rows are never mutated.
-- A user's current bid for an auction is their latest (MAX(id)) row; active only if PLACED/CHANGED.
-- This table is also the full bid audit trail (there is no separate bid_event table).
CREATE TABLE bid
(
    id            BIGSERIAL PRIMARY KEY,
    auction_id    BIGINT      NOT NULL, -- auction_ref (logical auction id); no DB FK, target is non-unique
    user_id       BIGINT      NOT NULL REFERENCES "user" (id), -- the bidder this event is about
    event_type    TEXT        NOT NULL CHECK (event_type IN ('PLACED', 'CHANGED', 'WITHDRAWN', 'MODERATED')),
    amount        NUMERIC(12, 2), -- amount for PLACED/CHANGED; snapshot of the prior amount on WITHDRAWN/MODERATED
    actor_user_id BIGINT      NOT NULL REFERENCES "user" (id), -- self, or the admin for MODERATED
    reason        TEXT,           -- moderation reason, else NULL
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    CHECK (event_type NOT IN ('PLACED', 'CHANGED') OR amount IS NOT NULL), -- active bids must carry an amount
    CHECK (event_type = 'MODERATED' OR reason IS NULL)                     -- reason is moderation-only
);
-- Auction-scoped latest-row-per-user lookups (existsActiveBidForAuction, findEligibleBids, findCurrentBid).
CREATE INDEX idx_bid_current ON bid (auction_id, user_id, id DESC);
-- User-scoped latest-row-per-auction lookups (findCurrentActiveBidsForUser); leading user_id also covers plain user_id filters.
CREATE INDEX idx_bid_by_user ON bid (user_id, auction_id, id DESC);

CREATE TABLE auction_result
(
    id                  BIGSERIAL PRIMARY KEY,
    auction_id          BIGINT      NOT NULL UNIQUE, -- auction_ref (logical auction id); no DB FK, target is non-unique
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

CREATE TABLE shedlock
(
    name       VARCHAR(64) PRIMARY KEY,
    lock_until TIMESTAMP    NOT NULL,
    locked_at  TIMESTAMP    NOT NULL,
    locked_by  VARCHAR(255) NOT NULL
);

INSERT INTO auction (auction_ref, item_id, title, description, category, lifecycle_status, start_price, current_value, currency)
VALUES
    (nextval('auction_ref_seq'), 'TEST-00001', 'Test auction 1', 'Seeded test auction 1', 'test', 'DRAFT', 100.00, 100.00, 'EUR'),
    (nextval('auction_ref_seq'), 'TEST-00002', 'Test auction 2', 'Seeded test auction 2', 'test', 'DRAFT', 200.00, 200.00, 'EUR'),
    (nextval('auction_ref_seq'), 'TEST-00003', 'Test auction 3', 'Seeded test auction 3', 'test', 'DRAFT', 300.00, 300.00, 'EUR'),
    (nextval('auction_ref_seq'), 'TEST-00004', 'Test auction 4', 'Seeded test auction 4', 'test', 'DRAFT', 400.00, 400.00, 'EUR'),
    (nextval('auction_ref_seq'), 'TEST-00005', 'Test auction 5', 'Seeded test auction 5', 'test', 'DRAFT', 500.00, 500.00, 'EUR');

-- Active auctions: window opened yesterday at 00:00 UTC, closes 30 days later at 23:59:59 UTC.
-- Anchored to UTC explicitly (not CURRENT_DATE) so the window doesn't shift with the connecting session's timezone.
INSERT INTO auction (auction_ref, item_id, title, description, category, lifecycle_status, start_price, current_value, currency, starts_at, ends_at)
VALUES
    (nextval('auction_ref_seq'), 'TEST-ACTIVE-00001', 'Active test auction 1', 'Seeded active test auction 1', 'test', 'ACTIVE', 100.00, 100.00, 'EUR',
     (date_trunc('day', now() AT TIME ZONE 'UTC') - INTERVAL '1 day') AT TIME ZONE 'UTC',
     (date_trunc('day', now() AT TIME ZONE 'UTC') - INTERVAL '1 day' + INTERVAL '30 days 23:59:59') AT TIME ZONE 'UTC'),
    (nextval('auction_ref_seq'), 'TEST-ACTIVE-00002', 'Active test auction 2', 'Seeded active test auction 2', 'test', 'ACTIVE', 200.00, 200.00, 'EUR',
     (date_trunc('day', now() AT TIME ZONE 'UTC') - INTERVAL '1 day') AT TIME ZONE 'UTC',
     (date_trunc('day', now() AT TIME ZONE 'UTC') - INTERVAL '1 day' + INTERVAL '30 days 23:59:59') AT TIME ZONE 'UTC'),
    (nextval('auction_ref_seq'), 'TEST-ACTIVE-00003', 'Active test auction 3', 'Seeded active test auction 3', 'test', 'ACTIVE', 300.00, 300.00, 'EUR',
     (date_trunc('day', now() AT TIME ZONE 'UTC') - INTERVAL '1 day') AT TIME ZONE 'UTC',
     (date_trunc('day', now() AT TIME ZONE 'UTC') - INTERVAL '1 day' + INTERVAL '30 days 23:59:59') AT TIME ZONE 'UTC'),
    (nextval('auction_ref_seq'), 'TEST-ACTIVE-00004', 'Active test auction 4', 'Seeded active test auction 4', 'test', 'ACTIVE', 400.00, 400.00, 'EUR',
     (date_trunc('day', now() AT TIME ZONE 'UTC') - INTERVAL '1 day') AT TIME ZONE 'UTC',
     (date_trunc('day', now() AT TIME ZONE 'UTC') - INTERVAL '1 day' + INTERVAL '30 days 23:59:59') AT TIME ZONE 'UTC'),
    (nextval('auction_ref_seq'), 'TEST-ACTIVE-00005', 'Active test auction 5', 'Seeded active test auction 5', 'test', 'ACTIVE', 500.00, 500.00, 'EUR',
     (date_trunc('day', now() AT TIME ZONE 'UTC') - INTERVAL '1 day') AT TIME ZONE 'UTC',
     (date_trunc('day', now() AT TIME ZONE 'UTC') - INTERVAL '1 day' + INTERVAL '30 days 23:59:59') AT TIME ZONE 'UTC'),
    (nextval('auction_ref_seq'), 'TEST-ACTIVE-00006', 'Active test auction 6', 'Seeded active test auction 6', 'test', 'ACTIVE', 600.00, 600.00, 'EUR',
     (date_trunc('day', now() AT TIME ZONE 'UTC') - INTERVAL '1 day') AT TIME ZONE 'UTC',
     (date_trunc('day', now() AT TIME ZONE 'UTC') - INTERVAL '1 day' + INTERVAL '30 days 23:59:59') AT TIME ZONE 'UTC'),
    (nextval('auction_ref_seq'), 'TEST-ACTIVE-00007', 'Active test auction 7', 'Seeded active test auction 7', 'test', 'ACTIVE', 700.00, 700.00, 'EUR',
     (date_trunc('day', now() AT TIME ZONE 'UTC') - INTERVAL '1 day') AT TIME ZONE 'UTC',
     (date_trunc('day', now() AT TIME ZONE 'UTC') - INTERVAL '1 day' + INTERVAL '30 days 23:59:59') AT TIME ZONE 'UTC'),
    (nextval('auction_ref_seq'), 'TEST-ACTIVE-00008', 'Active test auction 8', 'Seeded active test auction 8', 'test', 'ACTIVE', 800.00, 800.00, 'EUR',
     (date_trunc('day', now() AT TIME ZONE 'UTC') - INTERVAL '1 day') AT TIME ZONE 'UTC',
     (date_trunc('day', now() AT TIME ZONE 'UTC') - INTERVAL '1 day' + INTERVAL '30 days 23:59:59') AT TIME ZONE 'UTC'),
    (nextval('auction_ref_seq'), 'TEST-ACTIVE-00009', 'Active test auction 9', 'Seeded active test auction 9', 'test', 'ACTIVE', 900.00, 900.00, 'EUR',
     (date_trunc('day', now() AT TIME ZONE 'UTC') - INTERVAL '1 day') AT TIME ZONE 'UTC',
     (date_trunc('day', now() AT TIME ZONE 'UTC') - INTERVAL '1 day' + INTERVAL '30 days 23:59:59') AT TIME ZONE 'UTC'),
    (nextval('auction_ref_seq'), 'TEST-ACTIVE-00010', 'Active test auction 10', 'Seeded active test auction 10', 'test', 'ACTIVE', 1000.00, 1000.00, 'EUR',
     (date_trunc('day', now() AT TIME ZONE 'UTC') - INTERVAL '1 day') AT TIME ZONE 'UTC',
     (date_trunc('day', now() AT TIME ZONE 'UTC') - INTERVAL '1 day' + INTERVAL '30 days 23:59:59') AT TIME ZONE 'UTC');

-- Unsold auctions: window opened 60 days ago, closed 30 days ago — already ended with no bids.
INSERT INTO auction (auction_ref, item_id, title, description, category, lifecycle_status, start_price, current_value, currency, starts_at, ends_at)
VALUES
    (nextval('auction_ref_seq'), 'TEST-UNSOLD-00001', 'Unsold test auction 1', 'Seeded unsold test auction 1', 'test', 'UNSOLD', 100.00, 100.00, 'EUR',
     (date_trunc('day', now() AT TIME ZONE 'UTC') - INTERVAL '60 days') AT TIME ZONE 'UTC',
     (date_trunc('day', now() AT TIME ZONE 'UTC') - INTERVAL '30 days') AT TIME ZONE 'UTC'),
    (nextval('auction_ref_seq'), 'TEST-UNSOLD-00002', 'Unsold test auction 2', 'Seeded unsold test auction 2', 'test', 'UNSOLD', 200.00, 200.00, 'EUR',
     (date_trunc('day', now() AT TIME ZONE 'UTC') - INTERVAL '60 days') AT TIME ZONE 'UTC',
     (date_trunc('day', now() AT TIME ZONE 'UTC') - INTERVAL '30 days') AT TIME ZONE 'UTC');
