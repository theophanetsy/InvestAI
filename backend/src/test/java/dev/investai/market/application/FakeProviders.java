package dev.investai.market.application;

import dev.investai.market.domain.DailyClose;
import dev.investai.market.domain.HistoricalClose;
import dev.investai.market.domain.InstrumentId;
import dev.investai.market.domain.Isin;
import dev.investai.market.domain.Mic;
import dev.investai.market.domain.MifirTrade;
import dev.investai.market.domain.PriceSource;
import dev.investai.market.domain.SessionCloseCalculator;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Currency;

/** Test doubles of the market data sources: synthetic data, no network. */
final class FakeProviders {

  private FakeProviders() {}

  static MifirTrade trade(Isin isin, String time, String price) {
    return new MifirTrade(
        isin,
        Instant.parse(time),
        new BigDecimal(price),
        BigDecimal.TEN,
        Currency.getInstance("EUR"),
        SessionCloseCalculator.CENTRAL_ORDER_BOOK,
        Mic.XPAR);
  }

  static DailyClose euronextClose(InstrumentId instrument, LocalDate day, String price) {
    return new DailyClose(
        instrument, day, new BigDecimal(price), Currency.getInstance("EUR"), PriceSource.EURONEXT);
  }

  static DailyClose euronextClose(
      InstrumentId instrument, LocalDate day, String price, long volume, int tradeCount) {
    return new DailyClose(
        instrument,
        day,
        new BigDecimal(price),
        Currency.getInstance("EUR"),
        PriceSource.EURONEXT,
        volume,
        tradeCount);
  }

  static HistoricalClose close(String date, String price) {
    return new HistoricalClose(LocalDate.parse(date), new BigDecimal(price));
  }

  static HistoricalClose close(String date, String price, String volume) {
    return new HistoricalClose(
        LocalDate.parse(date), new BigDecimal(price), new BigDecimal(volume));
  }
}
