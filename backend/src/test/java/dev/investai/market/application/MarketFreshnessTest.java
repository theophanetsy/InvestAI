package dev.investai.market.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.investai.market.domain.DailyPriceRepository;
import dev.investai.market.domain.IndicatorRepository;
import dev.investai.market.domain.IndicatorRun;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MarketFreshnessTest {

  /** Tuesday 20 October 2026, 00:30 in Paris but still Monday 19 in UTC. */
  private static final Instant TUESDAY_JUST_AFTER_MIDNIGHT_IN_PARIS =
      Instant.parse("2026-10-19T22:30:00Z");

  private final DailyPriceRepository prices = mock(DailyPriceRepository.class);
  private final IndicatorRepository indicators = mock(IndicatorRepository.class);
  private MarketFreshness freshness;

  @BeforeEach
  void setUp() {
    freshness =
        new MarketFreshness(
            prices, indicators, new MutableClock(TUESDAY_JUST_AFTER_MIDNIGHT_IN_PARIS));
  }

  @Test
  void latestCloseIsLookedUpToTodayIncludedInParis() {
    var friday = LocalDate.parse("2026-10-16");
    // Today in Paris is Tuesday 20: closes up to that day included, i.e. before Wednesday 21.
    when(prices.latestSessionBefore(LocalDate.parse("2026-10-21"))).thenReturn(Optional.of(friday));
    var run = new IndicatorRun(friday, Instant.parse("2026-10-19T08:00:00Z"));
    when(indicators.lastRun()).thenReturn(Optional.of(run));

    var current = freshness.current();

    assertThat(current.closesAsOf()).isEqualTo(friday);
    assertThat(current.lastRun()).isEqualTo(run);
  }

  @Test
  void emptyDatabaseHasNeitherClosesNorComputation() {
    when(prices.latestSessionBefore(LocalDate.parse("2026-10-21"))).thenReturn(Optional.empty());
    when(indicators.lastRun()).thenReturn(Optional.empty());

    var current = freshness.current();

    assertThat(current.closesAsOf()).isNull();
    assertThat(current.lastRun()).isNull();
  }
}
