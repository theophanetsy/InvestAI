package dev.investai.market.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Currency;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class PriceMergeTest {

  private static final LocalDate DAY = LocalDate.parse("2026-10-16");
  private static final InstrumentId ID = InstrumentId.newId();

  @Test
  void newClosesAreWritten() {
    DailyClose close = close("10", PriceSource.EODHD);

    PriceMerge result = PriceMerge.of(List.of(close), Map.of());

    assertThat(result.toWrite()).containsExactly(close);
    assertThat(result.conflicts()).isEmpty();
  }

  @Test
  void euronextIsNeverReplacedByEodhdAndADifferenceIsReported() {
    DailyClose official = close("10", PriceSource.EURONEXT);

    PriceMerge result =
        PriceMerge.of(List.of(close("10.5", PriceSource.EODHD)), Map.of(official.key(), official));

    assertThat(result.toWrite()).isEmpty();
    assertThat(result.conflicts()).containsExactly(official.key());
  }

  @Test
  void euronextReplacesEodhdAndADifferenceIsReported() {
    DailyClose fallback = close("10.5", PriceSource.EODHD);
    DailyClose official = close("10", PriceSource.EURONEXT);

    PriceMerge result = PriceMerge.of(List.of(official), Map.of(fallback.key(), fallback));

    assertThat(result.toWrite()).containsExactly(official);
    assertThat(result.conflicts()).containsExactly(official.key());
  }

  @Test
  void equalPricesFromBothSourcesAreNotAConflict() {
    DailyClose official = close("10.0000", PriceSource.EURONEXT);

    PriceMerge result =
        PriceMerge.of(List.of(close("10", PriceSource.EODHD)), Map.of(official.key(), official));

    assertThat(result.toWrite()).isEmpty();
    assertThat(result.conflicts()).isEmpty();
  }

  @Test
  void sameSourceIsRewrittenWithoutConflict() {
    DailyClose previous = close("10", PriceSource.EURONEXT);
    DailyClose again = close("11", PriceSource.EURONEXT);

    PriceMerge result = PriceMerge.of(List.of(again), Map.of(previous.key(), previous));

    assertThat(result.toWrite()).containsExactly(again);
    assertThat(result.conflicts()).isEmpty();
  }

  private static DailyClose close(String price, PriceSource source) {
    return new DailyClose(ID, DAY, new BigDecimal(price), Currency.getInstance("EUR"), source);
  }
}
