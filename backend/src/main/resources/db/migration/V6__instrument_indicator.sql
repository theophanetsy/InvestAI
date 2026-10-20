-- Indicators of every instrument (phase 2.4, spec docs/specs/phase-2-04-indicators-dashboard.md),
-- computed daily after the collection on raw closes: never adjusted for splits, bonus shares or
-- dividends. One row per instrument, replaced by each computation (idempotent upsert); read by the
-- screening of every Paris instrument and, later, by the tools of the advice agent.
--
-- Each indicator is a value and a status; the value is present exactly when the status is
-- AVAILABLE (INSUFFICIENT_HISTORY: fewer than 80 % of the sessions of the window have a close;
-- NOT_COMPUTABLE: undefined, e.g. the correlation of a price that never moved). Values without
-- precision limit except where bounded by definition: one absurd value (bad print of a penny
-- stock) must not make the computation of every instrument fail.

CREATE DOMAIN indicator_status AS varchar(24)
    CHECK (VALUE IN ('AVAILABLE', 'INSUFFICIENT_HISTORY', 'NOT_COMPUTABLE'));

CREATE TABLE instrument_indicator (
    instrument_id                    uuid             PRIMARY KEY REFERENCES instrument (id),
    -- last session of every window
    as_of                            date             NOT NULL,
    computed_at                      timestamptz      NOT NULL,
    -- closes present among the 253 sessions of the one-year window
    closes_one_year                  smallint         NOT NULL CHECK (closes_one_year BETWEEN 0 AND 253),
    -- change of the close in percent
    performance_one_month            numeric,
    performance_one_month_status     indicator_status NOT NULL,
    performance_three_months         numeric,
    performance_three_months_status  indicator_status NOT NULL,
    performance_six_months           numeric,
    performance_six_months_status    indicator_status NOT NULL,
    performance_one_year             numeric,
    performance_one_year_status      indicator_status NOT NULL,
    -- annualised volatility of the daily returns over one year, in percent
    volatility                       numeric          CHECK (volatility >= 0),
    volatility_status                indicator_status NOT NULL,
    -- largest fall from a high over one year, in percent (zero or negative)
    max_drawdown                     numeric(5, 2)    CHECK (max_drawdown BETWEEN -100 AND 0),
    max_drawdown_status              indicator_status NOT NULL,
    -- compared with the MSCI World ETF (CW8) and the CAC 40 ETF (CAC) over one year
    msci_world_outperformance        numeric,
    msci_world_outperformance_status indicator_status NOT NULL,
    msci_world_correlation           numeric(3, 2)    CHECK (msci_world_correlation BETWEEN -1 AND 1),
    msci_world_correlation_status    indicator_status NOT NULL,
    msci_world_beta                  numeric,
    msci_world_beta_status           indicator_status NOT NULL,
    cac40_outperformance             numeric,
    cac40_outperformance_status      indicator_status NOT NULL,
    cac40_correlation                numeric(3, 2)    CHECK (cac40_correlation BETWEEN -1 AND 1),
    cac40_correlation_status         indicator_status NOT NULL,
    cac40_beta                       numeric,
    cac40_beta_status                indicator_status NOT NULL,
    -- mean Euronext close × volume over three months, in the currency of the instrument
    average_daily_traded_amount            numeric          CHECK (average_daily_traded_amount >= 0),
    average_daily_traded_amount_status     indicator_status NOT NULL,
    CHECK ((performance_one_month_status = 'AVAILABLE') = (performance_one_month IS NOT NULL)),
    CHECK ((performance_three_months_status = 'AVAILABLE') = (performance_three_months IS NOT NULL)),
    CHECK ((performance_six_months_status = 'AVAILABLE') = (performance_six_months IS NOT NULL)),
    CHECK ((performance_one_year_status = 'AVAILABLE') = (performance_one_year IS NOT NULL)),
    CHECK ((volatility_status = 'AVAILABLE') = (volatility IS NOT NULL)),
    CHECK ((max_drawdown_status = 'AVAILABLE') = (max_drawdown IS NOT NULL)),
    CHECK ((msci_world_outperformance_status = 'AVAILABLE') = (msci_world_outperformance IS NOT NULL)),
    CHECK ((msci_world_correlation_status = 'AVAILABLE') = (msci_world_correlation IS NOT NULL)),
    CHECK ((msci_world_beta_status = 'AVAILABLE') = (msci_world_beta IS NOT NULL)),
    CHECK ((cac40_outperformance_status = 'AVAILABLE') = (cac40_outperformance IS NOT NULL)),
    CHECK ((cac40_correlation_status = 'AVAILABLE') = (cac40_correlation IS NOT NULL)),
    CHECK ((cac40_beta_status = 'AVAILABLE') = (cac40_beta IS NOT NULL)),
    CHECK ((average_daily_traded_amount_status = 'AVAILABLE') = (average_daily_traded_amount IS NOT NULL))
);

-- Whether the indicators are stale: latest session before today, latest write of a close.
CREATE INDEX daily_price_trading_date_idx ON daily_price (trading_date);
CREATE INDEX daily_price_collected_at_idx ON daily_price (collected_at);
