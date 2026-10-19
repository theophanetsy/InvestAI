-- Market data (phase 2.1, ADR-0009): instrument reference data, daily closes, collection journal,
-- EODHD call budget and catch-up queue. Conventions: backend/CLAUDE.md.

-- Accent-insensitive instrument search (trusted extension, shipped with the official image).
CREATE EXTENSION IF NOT EXISTS unaccent;

-- Listed instruments, unique per listing (ISIN + venue). Loaded from EODHD, never deleted: an
-- instrument missing from a later reference refresh keeps its history.
CREATE TABLE instrument (
    id                uuid         PRIMARY KEY,
    isin              char(12)     NOT NULL,
    mic               char(4)      NOT NULL,
    ticker            varchar(32)  NOT NULL,
    name              varchar(256) NOT NULL,
    type              varchar(8)   NOT NULL CHECK (type IN ('STOCK', 'ETF', 'FUND', 'OTHER')),
    currency          char(3)      NOT NULL,
    -- null when the instrument is not followed
    tracked_since     timestamptz,
    history_loaded    boolean      NOT NULL DEFAULT false,
    -- last reference refresh that listed the instrument
    reference_seen_at timestamptz  NOT NULL,
    created_at        timestamptz  NOT NULL,
    updated_at        timestamptz  NOT NULL,
    CONSTRAINT instrument_listing_key UNIQUE (isin, mic)
);

-- One raw ("as traded") close per instrument and session.
CREATE TABLE daily_price (
    instrument_id uuid          NOT NULL REFERENCES instrument (id),
    trading_date  date          NOT NULL,
    close         numeric(19, 4) NOT NULL CHECK (close > 0),
    currency      char(3)       NOT NULL,
    source        varchar(8)    NOT NULL CHECK (source IN ('EURONEXT', 'EODHD')),
    collected_at  timestamptz   NOT NULL,
    PRIMARY KEY (instrument_id, trading_date)
);

-- One row per Euronext file processed, and one per session caught up with EODHD (segment null).
CREATE TABLE market_collection (
    id           uuid        PRIMARY KEY,
    trading_date date        NOT NULL,
    segment      varchar(16) CHECK (segment IN ('EQUITIES', 'ETFS_FUNDS')),
    source       varchar(8)  NOT NULL CHECK (source IN ('EURONEXT', 'EODHD')),
    processed_at timestamptz NOT NULL,
    closes_count integer     NOT NULL CHECK (closes_count >= 0),
    CONSTRAINT market_collection_segment_source CHECK ((segment IS NULL) = (source = 'EODHD')),
    CONSTRAINT market_collection_session_key UNIQUE NULLS NOT DISTINCT (trading_date, segment)
);

-- EODHD calls consumed per UTC day (free plan: 20 per day).
CREATE TABLE eodhd_usage (
    usage_date date    PRIMARY KEY,
    calls      integer NOT NULL CHECK (calls > 0)
);

-- Closes still to fetch from EODHD after missed sessions: one entry per instrument, so that a
-- catch-up interrupted by the daily budget resumes where it stopped.
CREATE TABLE price_catch_up (
    instrument_id uuid        PRIMARY KEY REFERENCES instrument (id),
    from_date     date        NOT NULL,
    to_date       date        NOT NULL,
    requested_at  timestamptz NOT NULL,
    CHECK (from_date <= to_date)
);
