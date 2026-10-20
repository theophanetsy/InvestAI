package dev.investai.market.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.investai.market.IndicatorFixtures;
import dev.investai.market.MarketTestData;
import dev.investai.market.domain.CollectionJournal;
import dev.investai.market.domain.DailyClose;
import dev.investai.market.domain.DailyPriceRepository;
import dev.investai.market.domain.IndicatorRepository;
import dev.investai.market.domain.Instrument;
import dev.investai.market.domain.InstrumentRepository;
import dev.investai.market.domain.Listing;
import dev.investai.market.domain.Mic;
import dev.investai.market.domain.StoredIndicators;
import dev.investai.shared.domain.ResourceNotFoundException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Everything stored about one instrument, for the MCP tool getInstrument (spec 3.1). */
class IndicatorQueriesTest {

  private static final LocalDate FRIDAY = LocalDate.parse("2026-10-16");
  private static final Instant COMPUTED_AT = Instant.parse("2026-10-17T07:00:00Z");

  private final InstrumentRepository instruments = mock(InstrumentRepository.class);
  private final DailyPriceRepository prices = mock(DailyPriceRepository.class);
  private final IndicatorRepository indicators = mock(IndicatorRepository.class);
  private final IndicatorQueries queries =
      new IndicatorQueries(
          instruments,
          prices,
          mock(CollectionJournal.class),
          indicators,
          new IndicatorSettings(listing("LU168104359"), listing("FR000705278")),
          new MutableClock(COMPUTED_AT));
  private final Instrument airLiquide = IndicatorFixtures.airLiquide();

  @Test
  void snapshotGivesTheLatestCloseAndTheStoredIndicators() {
    when(instruments.findById(airLiquide.id())).thenReturn(Optional.of(airLiquide));
    DailyClose close = IndicatorFixtures.close(airLiquide.id(), FRIDAY, "170.5");
    when(prices.findLatest(airLiquide.id())).thenReturn(Optional.of(close));
    var stored =
        new StoredIndicators(airLiquide.id(), IndicatorFixtures.sampleSet(FRIDAY), COMPUTED_AT);
    when(indicators.find(airLiquide.id())).thenReturn(Optional.of(stored));

    assertThat(queries.snapshot(airLiquide.id()))
        .isEqualTo(new InstrumentSnapshot(airLiquide, close, stored));
  }

  @Test
  void snapshotBeforeAnyCloseOrComputationHasNeither() {
    when(instruments.findById(airLiquide.id())).thenReturn(Optional.of(airLiquide));
    when(prices.findLatest(airLiquide.id())).thenReturn(Optional.empty());
    when(indicators.find(airLiquide.id())).thenReturn(Optional.empty());

    assertThat(queries.snapshot(airLiquide.id()))
        .isEqualTo(new InstrumentSnapshot(airLiquide, null, null));
  }

  @Test
  void snapshotOfAnUnknownInstrumentIsNotFound() {
    when(instruments.findById(airLiquide.id())).thenReturn(Optional.empty());

    assertThatThrownBy(() -> queries.snapshot(airLiquide.id()))
        .isInstanceOfSatisfying(
            ResourceNotFoundException.class,
            e -> assertThat(e.code()).isEqualTo(InstrumentTracking.NOT_FOUND));
  }

  private static Listing listing(String isinBody) {
    return new Listing(MarketTestData.isin(isinBody), Mic.XPAR);
  }
}
