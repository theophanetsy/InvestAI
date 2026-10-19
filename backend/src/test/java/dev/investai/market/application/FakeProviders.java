package dev.investai.market.application;

import dev.investai.market.domain.DailyClose;
import dev.investai.market.domain.HistoricalClose;
import dev.investai.market.domain.Instrument;
import dev.investai.market.domain.InstrumentId;
import dev.investai.market.domain.Isin;
import dev.investai.market.domain.ListedInstrument;
import dev.investai.market.domain.MarketDataProvider;
import dev.investai.market.domain.MarketDataUnavailableException;
import dev.investai.market.domain.Mic;
import dev.investai.market.domain.MifirTrade;
import dev.investai.market.domain.PriceSource;
import dev.investai.market.domain.ReferenceData;
import dev.investai.market.domain.SessionCloseCalculator;
import dev.investai.market.domain.TradeFileSegment;
import dev.investai.market.domain.TradeFileSource;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Currency;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import org.jspecify.annotations.Nullable;

/** Test doubles of the market data sources: synthetic data, no network. */
final class FakeProviders {

  private FakeProviders() {}

  /** Euronext files held in memory, by segment. */
  static final class TradeFiles implements TradeFileSource {
    private final Map<TradeFileSegment, List<MifirTrade>> files =
        new EnumMap<>(TradeFileSegment.class);
    private final List<TradeFileSegment> downloads = new ArrayList<>();
    private boolean failing;

    void publish(TradeFileSegment segment, MifirTrade... trades) {
      files.put(segment, List.of(trades));
    }

    void failing(boolean value) {
      failing = value;
    }

    List<TradeFileSegment> downloads() {
      return downloads;
    }

    @Override
    public Summary read(TradeFileSegment segment, Consumer<MifirTrade> consumer) {
      downloads.add(segment);
      if (failing) {
        throw new MarketDataUnavailableException("Euronext " + segment + " failed: HTTP 503");
      }
      List<MifirTrade> trades = files.getOrDefault(segment, List.of());
      LocalDate session = null;
      for (MifirTrade trade : trades) {
        consumer.accept(trade);
        LocalDate day = SessionCloseCalculator.sessionOf(trade);
        session = session == null || day.isAfter(session) ? day : session;
      }
      return new Summary(session, trades.size(), 0);
    }
  }

  /** EODHD answering from memory and recording its calls. */
  static final class Eodhd implements MarketDataProvider {
    private final List<String> calls = new ArrayList<>();
    private final Map<Isin, List<HistoricalClose>> history = new HashMap<>();
    private List<ListedInstrument> reference = List.of();
    private boolean enabled = true;
    private boolean failing;

    void reference(ListedInstrument... instruments) {
      reference = List.of(instruments);
    }

    void history(Isin isin, HistoricalClose... closes) {
      history.put(isin, List.of(closes));
    }

    void enabled(boolean value) {
      enabled = value;
    }

    void failing(boolean value) {
      failing = value;
    }

    List<String> calls() {
      return calls;
    }

    @Override
    public boolean isEnabled() {
      return enabled;
    }

    @Override
    public ReferenceData referenceData() {
      calls.add("exchange-symbol-list");
      failIfAsked();
      return new ReferenceData(reference, 1);
    }

    @Override
    public List<HistoricalClose> dailyCloses(Instrument instrument, LocalDate from, LocalDate to) {
      calls.add("eod " + instrument.ticker() + " " + from + " " + to);
      failIfAsked();
      return history.getOrDefault(instrument.listing().isin(), List.of()).stream()
          .filter(close -> !close.date().isBefore(from) && !close.date().isAfter(to))
          .toList();
    }

    private void failIfAsked() {
      if (failing) {
        throw new MarketDataUnavailableException("EODHD eod failed: ResourceAccessException");
      }
    }
  }

  /** A clock the test moves forward. */
  static final class MutableClock extends Clock {
    private Instant now;

    MutableClock(Instant now) {
      this.now = now;
    }

    void set(Instant instant) {
      now = instant;
    }

    @Override
    public ZoneId getZone() {
      return ZoneId.of("UTC");
    }

    @Override
    public Clock withZone(@Nullable ZoneId zone) {
      return zone == null ? this : Clock.fixed(now, zone);
    }

    @Override
    public Instant instant() {
      return now;
    }
  }

  static MifirTrade trade(Isin isin, String time, String price) {
    return new MifirTrade(
        isin,
        Instant.parse(time),
        new BigDecimal(price),
        Currency.getInstance("EUR"),
        SessionCloseCalculator.CENTRAL_ORDER_BOOK,
        Mic.XPAR);
  }

  static DailyClose euronextClose(InstrumentId instrument, LocalDate day, String price) {
    return new DailyClose(
        instrument, day, new BigDecimal(price), Currency.getInstance("EUR"), PriceSource.EURONEXT);
  }

  static HistoricalClose close(String date, String price) {
    return new HistoricalClose(LocalDate.parse(date), new BigDecimal(price));
  }
}
