package dev.investai.market.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Currency;

/**
 * One trade of a Euronext deferred trade file (MiFIR post-trade transparency).
 *
 * @param isin traded instrument ({@code MifidInstrumentID})
 * @param tradingTime execution time, UTC ({@code TradingDateTime})
 * @param price execution price as published ({@code MifidPrice})
 * @param currency price currency ({@code MifidCurrency})
 * @param mechanism market mechanism ({@code MmtMarketMechanism}: 1 = central limit order book,
 *     auctions included)
 * @param venue execution venue ({@code Venue})
 */
public record MifirTrade(
    Isin isin,
    Instant tradingTime,
    BigDecimal price,
    Currency currency,
    int mechanism,
    Mic venue) {}
