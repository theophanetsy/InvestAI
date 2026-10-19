package dev.investai.market.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Currency;
import org.jspecify.annotations.Nullable;

/**
 * One trade of a Euronext deferred trade file (MiFIR post-trade transparency).
 *
 * @param isin traded instrument ({@code MifidInstrumentID})
 * @param tradingTime execution time, UTC ({@code TradingDateTime})
 * @param price execution price as published ({@code MifidPrice})
 * @param quantity units traded as published ({@code MifidQuantity}), {@code null} when unreadable:
 *     the trade still counts for the close, the volume of the session becomes unknown
 * @param currency price currency ({@code MifidCurrency})
 * @param mechanism market mechanism ({@code MmtMarketMechanism}: 1 = central limit order book,
 *     auctions included)
 * @param venue execution venue ({@code Venue})
 */
public record MifirTrade(
    Isin isin,
    Instant tradingTime,
    BigDecimal price,
    @Nullable BigDecimal quantity,
    Currency currency,
    int mechanism,
    Mic venue) {}
