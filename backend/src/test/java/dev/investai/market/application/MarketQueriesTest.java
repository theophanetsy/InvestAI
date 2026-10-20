package dev.investai.market.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.investai.market.IndicatorFixtures;
import dev.investai.market.domain.DailyClose;
import dev.investai.market.domain.DailyPriceRepository;
import dev.investai.market.domain.IndicatorWindow;
import dev.investai.market.domain.InstrumentId;
import dev.investai.market.domain.InstrumentRepository;
import dev.investai.shared.domain.ResourceNotFoundException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Recent closes of an instrument, for the MCP tool getPriceHistory (spec 3.1, criterion 22). */
class MarketQueriesTest {

  private static final LocalDate FRIDAY = LocalDate.parse("2026-10-16");

  /**
   * One month = 21 sessions, so 22 closes: the 12 sessions of October 1 to 16, 2026 and the last 10
   * of September (17 to 30), no holiday in between.
   */
  private static final LocalDate MONTH_START = LocalDate.parse("2026-09-17");

  private final InstrumentRepository instruments = mock(InstrumentRepository.class);
  private final DailyPriceRepository prices = mock(DailyPriceRepository.class);
  private final MarketQueries queries =
      new MarketQueries(
          instruments, prices, new MutableClock(Instant.parse("2026-10-20T08:00:00Z")));
  private final InstrumentId id = IndicatorFixtures.airLiquide().id();

  @Test
  void historyEndsAtTheLatestCloseAndKeepsOnlyTheSessionsOfThePeriod() {
    when(instruments.findById(id)).thenReturn(Optional.of(IndicatorFixtures.airLiquide()));
    DailyClose friday = IndicatorFixtures.close(id, FRIDAY, "150.10");
    DailyClose thursday = IndicatorFixtures.close(id, FRIDAY.minusDays(1), "149");
    // A close on a Saturday is not a session of the calendar: left out, like the indicators do.
    DailyClose saturday = IndicatorFixtures.close(id, LocalDate.parse("2026-10-10"), "1");
    when(prices.findLatest(id)).thenReturn(Optional.of(friday));
    when(prices.findBetween(id, MONTH_START, FRIDAY))
        .thenReturn(List.of(saturday, thursday, friday));

    PriceHistory history = queries.history(id, IndicatorWindow.ONE_MONTH);

    assertThat(history.sessions()).hasSize(22).startsWith(MONTH_START).endsWith(FRIDAY);
    assertThat(history.closes()).containsExactly(thursday, friday);
    assertThat(history.missingSessions()).isEqualTo(20);
  }

  @Test
  void historyOfAnInstrumentWithoutCloseIsEmpty() {
    when(instruments.findById(id)).thenReturn(Optional.of(IndicatorFixtures.airLiquide()));
    when(prices.findLatest(id)).thenReturn(Optional.empty());

    PriceHistory history = queries.history(id, IndicatorWindow.ONE_YEAR);

    assertThat(history.sessions()).isEmpty();
    assertThat(history.closes()).isEmpty();
    assertThat(history.missingSessions()).isZero();
  }

  @Test
  void historyOfAnUnknownInstrumentIsNotFound() {
    when(instruments.findById(id)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> queries.history(id, IndicatorWindow.ONE_MONTH))
        .isInstanceOfSatisfying(
            ResourceNotFoundException.class,
            e -> assertThat(e.code()).isEqualTo(InstrumentTracking.NOT_FOUND));
  }
}
