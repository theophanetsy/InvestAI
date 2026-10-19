package dev.investai.market.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Currency;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

class SessionCloseCalculatorTest {

  private static final String CLOSE_TIME = "15:35:00";
  private static final String TOTAL_PRICE = "55.1000000";
  private static final String QUANTITY = "10.0";
  private static final String NOON = "12:00:00";
  private static final String GEVELOT_PRICE = "180.0000000";
  private static final String NOON_PRICE = "55.0000000";
  private static final String LARGE_QUANTITY = "1000";

  private static final LocalDate SESSION = LocalDate.parse("2026-10-16");
  private static final Currency EUR = Currency.getInstance("EUR");
  private static final Isin AIR_LIQUIDE = new Isin("FR0000120073");
  private static final Isin GEVELOT = new Isin("FR0000033888");
  private static final Isin TOTAL = new Isin("FR0000120271");
  private static final Isin ASML = new Isin("NL0010273215");
  private static final int ORDER_BOOK = 1;
  private static final int OFF_BOOK = 3;

  private final Instrument airLiquide = instrument(AIR_LIQUIDE, Mic.XPAR, true);
  private final Instrument gevelot = instrument(GEVELOT, Mic.ALXP, false);
  private final Instrument total = instrument(TOTAL, Mic.XPAR, false);
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
  void everyInstrumentOfTheReferenceDataGetsItsCloseTrackedOrNot() {
    calculator.accept(trade(AIR_LIQUIDE, CLOSE_TIME, "173.1600000", ORDER_BOOK, Mic.XPAR));
    calculator.accept(trade(GEVELOT, CLOSE_TIME, GEVELOT_PRICE, ORDER_BOOK, Mic.ALXP));
    calculator.accept(trade(TOTAL, CLOSE_TIME, TOTAL_PRICE, ORDER_BOOK, Mic.XPAR));

    assertThat(calculator.result().closes())
        .extracting(DailyClose::instrumentId)
        .containsExactlyInAnyOrder(airLiquide.id(), gevelot.id(), total.id());
  }

  @Test
  void instrumentsOutsideTheReferenceDataAreIgnoredAndCounted() {
    calculator.accept(trade(ASML, CLOSE_TIME, "600.0000000", ORDER_BOOK, Mic.XPAR));
    calculator.accept(trade(ASML, "15:36:00", "601.0000000", OFF_BOOK, Mic.XPAR));
    calculator.accept(
        trade(new Isin("US0378331005"), CLOSE_TIME, "200.0000000", ORDER_BOOK, Mic.XPAR));

    SessionCloses result = calculator.result();

    assertThat(result.closes()).isEmpty();
    assertThat(result.unknownIsins()).isEqualTo(2);
    assertThat(result.unknownTrades()).isEqualTo(3);
  }

  @Test
  void distinctUnknownIsinsAreRememberedUpToALimitButEveryTradeIsCounted() {
    var bounded = new SessionCloseCalculator(List.of(total), SESSION, 1);
    bounded.accept(trade(ASML, CLOSE_TIME, "600.0000000", ORDER_BOOK, Mic.XPAR));
    bounded.accept(trade(AIR_LIQUIDE, CLOSE_TIME, "173.0000000", ORDER_BOOK, Mic.XPAR));
    bounded.accept(trade(GEVELOT, CLOSE_TIME, GEVELOT_PRICE, ORDER_BOOK, Mic.ALXP));

    SessionCloses result = bounded.result();

    assertThat(result.unknownIsins()).as("at least, capped").isEqualTo(1);
    assertThat(result.unknownTrades()).isEqualTo(3);
  }

  @Test
  void otherSessionsAreIgnored() {
    calculator.accept(
        new MifirTrade(
            TOTAL,
            Instant.parse("2026-10-15T15:35:00Z"),
            new BigDecimal("55"),
            new BigDecimal(QUANTITY),
            EUR,
            ORDER_BOOK,
            Mic.XPAR));
    calculator.accept(
        new MifirTrade(
            ASML,
            Instant.parse("2026-10-15T15:35:00Z"),
            new BigDecimal("600"),
            new BigDecimal(QUANTITY),
            EUR,
            ORDER_BOOK,
            Mic.XPAR));

    SessionCloses result = calculator.result();

    assertThat(result.closes()).isEmpty();
    assertThat(result.unknownTrades()).isZero();
  }

  @Test
  void instrumentWithoutTradeGetsNoClose() {
    calculator.accept(trade(TOTAL, CLOSE_TIME, "55.1200000", ORDER_BOOK, Mic.XPAR));

    assertThat(calculator.result().closes())
        .extracting(DailyClose::instrumentId)
        .containsExactly(total.id());
  }

  @Test
  void volumeAndTradeCountCoverTheTradesEligibleForTheClose() {
    calculator.accept(trade(TOTAL, "09:00:05", "54.9000000", "120.0", ORDER_BOOK, Mic.XPAR));
    calculator.accept(trade(TOTAL, CLOSE_TIME, TOTAL_PRICE, "30", ORDER_BOOK, Mic.XPAR));
    calculator.accept(trade(TOTAL, NOON, NOON_PRICE, "7", ORDER_BOOK, Mic.XPAR));
    // Not eligible: off book, another venue, another currency, another session.
    calculator.accept(trade(TOTAL, "12:00:01", NOON_PRICE, LARGE_QUANTITY, OFF_BOOK, Mic.XPAR));
    calculator.accept(trade(TOTAL, "12:00:02", NOON_PRICE, LARGE_QUANTITY, ORDER_BOOK, Mic.ALXP));
    calculator.accept(
        new MifirTrade(
            TOTAL,
            Instant.parse(SESSION + "T12:00:03Z"),
            new BigDecimal("60"),
            new BigDecimal(LARGE_QUANTITY),
            Currency.getInstance("USD"),
            ORDER_BOOK,
            Mic.XPAR));
    calculator.accept(
        new MifirTrade(
            TOTAL,
            Instant.parse("2026-10-15T12:00:00Z"),
            new BigDecimal("55"),
            new BigDecimal(LARGE_QUANTITY),
            EUR,
            ORDER_BOOK,
            Mic.XPAR));

    assertThat(calculator.result().closes())
        .singleElement()
        .satisfies(
            close -> {
              assertThat(close.close()).isEqualByComparingTo("55.10");
              assertThat(close.volume()).isEqualTo(157L);
              assertThat(close.tradeCount()).isEqualTo(3);
            });
  }

  @Test
  void unusableQuantityLeavesTheVolumeUnknownButKeepsTheCloseAndTheTradeCount() {
    calculator.accept(trade(TOTAL, NOON, NOON_PRICE, "10", ORDER_BOOK, Mic.XPAR));
    calculator.accept(trade(TOTAL, CLOSE_TIME, TOTAL_PRICE, null, ORDER_BOOK, Mic.XPAR));
    calculator.accept(trade(GEVELOT, NOON, GEVELOT_PRICE, "2.5", ORDER_BOOK, Mic.ALXP));
    calculator.accept(trade(AIR_LIQUIDE, NOON, "173.0000000", "-1", ORDER_BOOK, Mic.XPAR));

    assertThat(calculator.result().closes())
        .hasSize(3)
        .allSatisfy(
            close -> {
              assertThat(close.volume()).isNull();
              assertThat(close.tradeCount()).isPositive();
            })
        .filteredOn(close -> close.instrumentId().equals(total.id()))
        .singleElement()
        .satisfies(close -> assertThat(close.tradeCount()).isEqualTo(2));
  }

  @Test
  void volumeBeyondALongIsUnknown() {
    String huge = String.valueOf(Long.MAX_VALUE);
    calculator.accept(trade(TOTAL, NOON, NOON_PRICE, huge, ORDER_BOOK, Mic.XPAR));
    calculator.accept(trade(TOTAL, CLOSE_TIME, TOTAL_PRICE, "1", ORDER_BOOK, Mic.XPAR));

    assertThat(calculator.result().closes())
        .singleElement()
        .satisfies(close -> assertThat(close.volume()).isNull());
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
    // Gevelot is listed on Euronext Growth, the file says it traded on the regulated market.
    calculator.accept(trade(GEVELOT, "15:30:00", GEVELOT_PRICE, ORDER_BOOK, Mic.XPAR));

    SessionCloses result = calculator.result();

    assertThat(result.closes()).isEmpty();
    assertThat(result.tradedElsewhereOnly()).containsExactly(gevelot.listing());
  }

  @Test
  void tradesOnAnotherVenueAreNotReportedWhenTheListingVenueTraded() {
    calculator.accept(trade(TOTAL, "15:30:00", NOON_PRICE, ORDER_BOOK, new Mic("XMLI")));
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
            new BigDecimal(QUANTITY),
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
    return trade(isin, time, price, QUANTITY, mechanism, venue);
  }

  private static MifirTrade trade(
      Isin isin, String time, String price, @Nullable String quantity, int mechanism, Mic venue) {
    return new MifirTrade(
        isin,
        Instant.parse(SESSION + "T" + time + "Z"),
        new BigDecimal(price),
        quantity == null ? null : new BigDecimal(quantity),
        EUR,
        mechanism,
        venue);
  }

  private static Instrument instrument(Isin isin, Mic mic, boolean tracked) {
    return new Instrument(
        InstrumentId.newId(),
        new Listing(isin, mic),
        "T",
        "Name",
        InstrumentType.STOCK,
        EUR,
        tracked ? Instant.parse("2026-01-01T00:00:00Z") : null,
        tracked);
  }
}
