package dev.investai.market.application;

import dev.investai.market.domain.DailyPriceRepository;
import dev.investai.market.domain.EuronextCalendar;
import dev.investai.market.domain.IndicatorRepository;
import dev.investai.market.domain.IndicatorRun;
import java.time.Clock;
import java.time.LocalDate;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * How recent the market data is: the latest stored close and the latest computation of the
 * indicators (the health tool of the MCP server, phase 3.0). Read from the database only.
 */
@Service
@Transactional(readOnly = true)
public class MarketFreshness {

  private final DailyPriceRepository prices;
  private final IndicatorRepository indicators;
  private final Clock clock;

  /**
   * Creates the query.
   *
   * @param prices stored closes
   * @param indicators stored indicators
   * @param clock current time
   */
  public MarketFreshness(DailyPriceRepository prices, IndicatorRepository indicators, Clock clock) {
    this.prices = prices;
    this.indicators = indicators;
    this.clock = clock;
  }

  /**
   * Returns the current freshness of the market data.
   *
   * @return the latest close session (up to today in Paris) and the latest indicator computation
   */
  public Freshness current() {
    LocalDate today = LocalDate.now(clock.withZone(EuronextCalendar.ZONE));
    return new Freshness(
        prices.latestSessionBefore(today.plusDays(1)).orElse(null),
        indicators.lastRun().orElse(null));
  }

  /**
   * Freshness of the market data.
   *
   * @param closesAsOf session of the latest stored close, {@code null} when none is stored
   * @param lastRun latest computation of the indicators, {@code null} when it never ran
   */
  public record Freshness(@Nullable LocalDate closesAsOf, @Nullable IndicatorRun lastRun) {}
}
