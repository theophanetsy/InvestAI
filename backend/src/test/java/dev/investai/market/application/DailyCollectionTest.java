package dev.investai.market.application;

import static dev.investai.market.MarketTestData.EUR;
import static dev.investai.market.MarketTestData.isin;
import static dev.investai.market.application.FakeProviders.trade;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import dev.investai.market.MarketTestData;
import dev.investai.market.domain.CollectionJournal;
import dev.investai.market.domain.Instrument;
import dev.investai.market.domain.InstrumentId;
import dev.investai.market.domain.InstrumentRepository;
import dev.investai.market.domain.InstrumentType;
import dev.investai.market.domain.Listing;
import dev.investai.market.domain.Mic;
import dev.investai.market.domain.TradeFileSegment;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

/**
 * Decisions of the daily collection that do not need a database (spec 2.2, criteria 1 and 2).
 * Monday 19 October 2026, 10:00 in Paris: the expected session is Friday 16.
 */
@ExtendWith(OutputCaptureExtension.class)
class DailyCollectionTest {

  private static final Instant MONDAY_MORNING = Instant.parse("2026-10-19T08:00:00Z");

  private final FakeTradeFiles files = new FakeTradeFiles();
  private final InstrumentRepository instruments = mock(InstrumentRepository.class);
  private final CollectionJournal journal = mock(CollectionJournal.class);
  private final PriceRecorder recorder = mock(PriceRecorder.class);
  private final DailyCollection collection =
      new DailyCollection(files, instruments, journal, recorder, new MutableClock(MONDAY_MORNING));

  @Test
  void emptyReferenceDataDownloadsNothingAndKeepsTheSessionForLater() {
    given(instruments.findAll()).willReturn(List.of());

    assertThat(collection.collect()).isEqualTo(DailyCollection.Outcome.NO_REFERENCE_DATA);

    assertThat(files.downloads()).as("the file stays available until the next morning").isEmpty();
    verify(recorder, never()).recordSessionFile(any(), any(), any());
  }

  @Test
  void collectedFileLogsTheTradesOfIsinsOutsideTheReferenceData(CapturedOutput output) {
    var known = isin("FR000012007");
    given(instruments.findAll())
        .willReturn(
            List.of(
                new Instrument(
                    InstrumentId.newId(),
                    new Listing(known, Mic.XPAR),
                    "AI",
                    "Air Liquide",
                    InstrumentType.STOCK,
                    EUR,
                    null,
                    false,
                    MarketTestData.UNKNOWN_BY_RULE)));
    files.publish(
        TradeFileSegment.EQUITIES,
        trade(known, "2026-10-16T15:35:00Z", "173.16"),
        trade(isin("BE000000001"), "2026-10-16T15:35:00Z", "10"),
        trade(isin("BE000000001"), "2026-10-16T15:36:00Z", "10"),
        trade(isin("BE000000002"), "2026-10-16T15:36:00Z", "10"));
    files.publish(TradeFileSegment.ETFS_FUNDS, trade(known, "2026-10-16T15:00:00Z", "172"));

    assertThat(collection.collect()).isEqualTo(DailyCollection.Outcome.COLLECTED);

    assertThat(output.getOut().lines().filter(line -> line.contains("segment=EQUITIES")))
        .singleElement()
        .satisfies(
            line -> assertThat(line).contains("closes=1", "unknownIsins=2", "unknownTrades=3"));
  }
}
