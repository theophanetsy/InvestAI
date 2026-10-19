-- Activity of the session next to each close (phase 2.2): volume and number of trades, to judge
-- the liquidity of an instrument later. Both nullable: unknown for the closes stored before this
-- migration, for an EODHD close without volume, and when a quantity of the file is unusable.

ALTER TABLE daily_price
    ADD COLUMN volume      bigint  CHECK (volume >= 0),
    ADD COLUMN trade_count integer CHECK (trade_count >= 0);

COMMENT ON COLUMN daily_price.volume IS
    'Units traded in the session, null when unknown. EURONEXT: sum of MifidQuantity of the central '
    'order book trades (MmtMarketMechanism = 1) on the listing venue, in the instrument currency. '
    'EODHD: volume field as published (definition not documented by the provider).';

COMMENT ON COLUMN daily_price.trade_count IS
    'Number of trades in the session, null when unknown. EURONEXT only: the same trades as the '
    'volume. Always null for EODHD.';
