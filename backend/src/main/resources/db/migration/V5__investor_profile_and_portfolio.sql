-- Investor profile and declared PEA (phase 2.3, spec docs/specs/phase-2-03-profile-portfolio.md).
-- Single user: the profile and the cash are single rows, keyed by a constant (singleton = true)
-- so that saving them is an idempotent upsert and a second row cannot exist.

CREATE TABLE investor_profile (
    singleton        boolean        PRIMARY KEY DEFAULT true CHECK (singleton),
    amount_to_invest numeric(19, 4) NOT NULL CHECK (amount_to_invest > 0),
    currency         char(3)        NOT NULL CHECK (currency = 'EUR'),
    horizon_years    smallint       NOT NULL CHECK (horizon_years BETWEEN 1 AND 40),
    risk_profile     varchar(16)    NOT NULL
        CHECK (risk_profile IN ('PRUDENT', 'BALANCED', 'DYNAMIC', 'AGGRESSIVE')),
    exclusions       text[]         NOT NULL DEFAULT '{}' CHECK (cardinality(exclusions) <= 20),
    -- Free text typed by the owner: untrusted data, stored as typed.
    objectives       text           CHECK (char_length(objectives) <= 500),
    pea_opened_on    date,
    updated_at       timestamptz    NOT NULL
);

-- Lines copied from the broker: no transaction, no recomputed average price. One per instrument.
CREATE TABLE portfolio_line (
    id            uuid           PRIMARY KEY,
    instrument_id uuid           NOT NULL REFERENCES instrument (id),
    quantity      bigint         NOT NULL CHECK (quantity > 0),
    -- average purchase price shown by the broker, null when not given
    average_price numeric(19, 4) CHECK (average_price > 0),
    currency      char(3)        NOT NULL CHECK (currency = 'EUR'),
    created_at    timestamptz    NOT NULL,
    updated_at    timestamptz    NOT NULL,
    CONSTRAINT portfolio_line_instrument_key UNIQUE (instrument_id)
);

-- Cash available on the PEA; no row until the owner declares it.
CREATE TABLE portfolio_cash (
    singleton  boolean        PRIMARY KEY DEFAULT true CHECK (singleton),
    amount     numeric(19, 4) NOT NULL CHECK (amount >= 0),
    currency   char(3)        NOT NULL CHECK (currency = 'EUR'),
    updated_at timestamptz    NOT NULL
);
