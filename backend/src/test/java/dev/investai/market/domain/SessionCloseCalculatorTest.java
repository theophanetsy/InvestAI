package dev.investai.market.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Currency;
import java.util.List;
import org.junit.jupiter.api.Test;

class SessionCloseCalculatorTest {

  private static final String CLOSE_TIME = "15:35:00";
  private static final String TOTAL_PRICE = "55.1000000";

  private static final LocalDate SESSION = LocalDate.parse("2026-10-16");
  private static final Currency EUR = Currency.getInstance("EUR");
  private static final Isin AIR_LIQUIDE = new Isin("FR0000120073");
  private static final Isin GEVELOT = new Isin("FR0000033888");
  private static final Isin TOTAL = new Isin("FR0000120271");
  private static final int ORDER_BOOK = 1;
  private static final int OFF_BOOK = 3;

  private final Instrument airLiquide = tracked(AIR_LIQUIDE, Mic.XPAR);
  private final Instrument gevelot = tracked(GEVELOT, Mic.ALXP);
  private final Instrument total = tracked(TOTAL, Mic.XPAR);
  private final SessionCloseCalculator calculator =
      new SessionCloseCalculator(List.of(airLiquide, gevelot, total), SESSION);

  @Test
  void closeIsTheLastOrderBookTradeOnTheListingVenueInTimeOrder() {
    calculator.accept(trade(AIR_LIQUIDE, "15:35:24.250725", "173.1600000", ORDER_BOOK, Mic.XPAR));
    // Files are not sorted: an earlier trade read later must not win.
    calculator.accept(trade(AIR_LIQUIDE, "15:20:00", "172.0000000", ORDER_BOOK, Mic.XPAR));
    // Off-book trade after the close: ignored.
    calculator.accept(trade(AIR_LIQUIDE, "16:00:00", "170.0000000", OFF_BOOK, Mic.XPAR));

    SessionCloses result = calculator.result();

    assertThat(result.closes())
        .singleElement()
        .satisfies(
            close -> {
              assertThat(close.instrumentId()).isEqualTo(airLiquide.id());
              assertThat(close.tradingDate()).isEqualTo(SESSION);
              assertThat(close.close()).isEqualByComparingTo("173.16");
              assertThat(close.close().scale()).isEqualTo(4);
              assertThat(close.currency()).isEqualTo(EUR);
              assertThat(close.source()).isEqualTo(PriceSource.EURONEXT);
            });
    assertThat(result.ambiguousTimestamps()).isEmpty();
    assertThat(result.tradedElsewhereOnly()).isEmpty();
    assertThat(result.notStorable()).isEmpty();
  }

  @Test
  void untrackedInstrumentsAndOtherSessionsAreIgnored() {
    calculator.accept(
        trade(new Isin("NL0010273215"), CLOSE_TIME, "600.0000000", ORDER_BOOK, Mic.XPAR));
    calculator.accept(
        new MifirTrade(
            TOTAL,
            Instant.parse("2026-10-15T15:35:00Z"),
            new BigDecimal("55"),
            EUR,
            ORDER_BOOK,
            Mic.XPAR));

    assertThat(calculator.result().closes()).isEmpty();
  }

  @Test
  void instrumentWithoutTradeGetsNoClose() {
    calculator.accept(trade(TOTAL, CLOSE_TIME, "55.1200000", ORDER_BOOK, Mic.XPAR));

    assertThat(calculator.result().closes())
        .extracting(DailyClose::instrumentId)
        .containsExactly(total.id());
  }

  @Test
  void sameTimestampWithDifferentPricesKeepsFileOrderAndIsReported() {
    calculator.accept(trade(TOTAL, CLOSE_TIME, TOTAL_PRICE, ORDER_BOOK, Mic.XPAR));
    calculator.accept(trade(TOTAL, CLOSE_TIME, "55.2000000", ORDER_BOOK, Mic.XPAR));

    SessionCloses result = calculator.result();

    assertThat(result.closes())
        .singleElement()
        .extracting(DailyClose::close)
        .isEqualTo(price("55.2"));
    assertThat(result.ambiguousTimestamps()).containsExactly(total.listing());
  }

  @Test
  void sameTimestampWithSamePriceIsNotReported() {
    calculator.accept(trade(TOTAL, CLOSE_TIME, TOTAL_PRICE, ORDER_BOOK, Mic.XPAR));
    calculator.accept(trade(TOTAL, CLOSE_TIME, TOTAL_PRICE, ORDER_BOOK, Mic.XPAR));

    assertThat(calculator.result().ambiguousTimestamps()).isEmpty();
  }

  @Test
  void earlierTieIsForgottenOnceALaterTradeArrives() {
    calculator.accept(trade(TOTAL, "15:00:00", TOTAL_PRICE, ORDER_BOOK, Mic.XPAR));
    calculator.accept(trade(TOTAL, "15:00:00", "55.2000000", ORDER_BOOK, Mic.XPAR));
    calculator.accept(trade(TOTAL, CLOSE_TIME, "55.3000000", ORDER_BOOK, Mic.XPAR));

    assertThat(calculator.result().ambiguousTimestamps()).isEmpty();
  }

  @Test
  void tradesOnlyOnAnotherVenueAreReportedAsAWrongVenueRule() {
    // Gevelot is tracked on Euronext Growth, the file says it traded on the regulated market.
    calculator.accept(trade(GEVELOT, "15:30:00", "180.0000000", ORDER_BOOK, Mic.XPAR));

    SessionCloses result = calculator.result();

    assertThat(result.closes()).isEmpty();
    assertThat(result.tradedElsewhereOnly()).containsExactly(gevelot.listing());
  }

  @Test
  void tradesOnAnotherVenueAreNotReportedWhenTheListingVenueTraded() {
    calculator.accept(trade(TOTAL, "15:30:00", "55.0000000", ORDER_BOOK, new Mic("XMLI")));
    calculator.accept(trade(TOTAL, CLOSE_TIME, TOTAL_PRICE, ORDER_BOOK, Mic.XPAR));

    assertThat(calculator.result().tradedElsewhereOnly()).isEmpty();
  }

  @Test
  void closeWithMoreThanFourDecimalsIsNotStoredNorReplacedByAnEarlierTrade() {
    calculator.accept(trade(TOTAL, "15:00:00", "0.1750000", ORDER_BOOK, Mic.XPAR));
    calculator.accept(trade(TOTAL, CLOSE_TIME, "0.1750500", ORDER_BOOK, Mic.XPAR));

    SessionCloses result = calculator.result();

    assertThat(result.closes()).isEmpty();
    assertThat(result.notStorable()).containsExactly(total.listing());
  }

  @Test
  void tradesInAnotherCurrencyThanTheInstrumentAreIgnoredAndReported() {
    calculator.accept(
        new MifirTrade(
            TOTAL,
            Instant.parse(SESSION + "T15:35:00Z"),
            new BigDecimal("60"),
            Currency.getInstance("USD"),
            ORDER_BOOK,
            Mic.XPAR));

    SessionCloses result = calculator.result();

    assertThat(result.closes()).isEmpty();
    assertThat(result.otherCurrency()).containsExactly(total.listing());
  }

  private static BigDecimal price(String value) {
    return new BigDecimal(value).setScale(Prices.SCALE);
  }

  private static MifirTrade trade(Isin isin, String time, String price, int mechanism, Mic venue) {
    return new MifirTrade(
        isin,
        Instant.parse(SESSION + "T" + time + "Z"),
        new BigDecimal(price),
        EUR,
        mechanism,
        venue);
  }

  private static Instrument tracked(Isin isin, Mic mic) {
    return new Instrument(
        InstrumentId.newId(),
        new Listing(isin, mic),
        "T",
        "Name",
        InstrumentType.STOCK,
        EUR,
        Instant.parse("2026-01-01T00:00:00Z"),
        true);
  }
}
