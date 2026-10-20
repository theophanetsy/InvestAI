package dev.investai.market;

import dev.investai.market.domain.BenchmarkComparison;
import dev.investai.market.domain.DailyClose;
import dev.investai.market.domain.DailyPriceRepository;
import dev.investai.market.domain.Indicator;
import dev.investai.market.domain.IndicatorSet;
import dev.investai.market.domain.IndicatorWindow;
import dev.investai.market.domain.Instrument;
import dev.investai.market.domain.InstrumentId;
import dev.investai.market.domain.InstrumentRepository;
import dev.investai.market.domain.InstrumentType;
import dev.investai.market.domain.Isin;
import dev.investai.market.domain.ListedInstrument;
import dev.investai.market.domain.Listing;
import dev.investai.market.domain.Mic;
import dev.investai.market.domain.PriceSource;
import dev.investai.market.domain.YearWindow;
import dev.investai.shared.persistence.Timestamps;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.IntFunction;
import java.util.stream.IntStream;
import org.springframework.jdbc.core.simple.JdbcClient;

/** Synthetic instruments and closes of the indicator tests (spec 2.4). Never real market data. */
public final class IndicatorFixtures {

  /**
   * Pseudo-random but deterministic closes between 10 and 30 on every weekday of a period (holidays
   * included: ignored by the computation), Euronext volume 1,000, for every instrument.
   */
  private static final String SYNTHETIC_CLOSES =
      """
      INSERT INTO daily_price (instrument_id, trading_date, close, currency, source, volume,
                               trade_count, collected_at)
      SELECT i.id, d.day::date,
             10 + (abs(hashtext(i.id::text || d.day::date::text)) % 2000) / 100.0,
             'EUR', 'EURONEXT', 1000, 10, :at
      FROM instrument i
      CROSS JOIN generate_series(CAST(:from AS date), CAST(:to AS date), interval '1 day') AS d(day)
      WHERE extract(isodow FROM d.day) < 6
      """;

  private IndicatorFixtures() {}

  /**
   * Adds an instrument to the reference data.
   *
   * @param instruments the reference data
   * @param listing ISIN and venue
   * @param type kind of instrument
   * @param name its name (also used as ticker, truncated)
   * @param at time of the reference refresh
   * @return the stored instrument
   */
  public static Instrument instrument(
      InstrumentRepository instruments,
      Listing listing,
      InstrumentType type,
      String name,
      Instant at) {
    String ticker =
        name.substring(0, Math.min(name.length(), 4)).toUpperCase(java.util.Locale.ROOT);
    instruments.upsertReference(
        List.of(new ListedInstrument(listing, ticker, name, type, MarketTestData.EUR)), at);
    return instruments.findByListing(listing).orElseThrow();
  }

  /**
   * Air Liquide on Euronext Paris, tracked, eligibility unknown: an instrument of web tests, which
   * never reach the database.
   *
   * @return the instrument
   */
  public static Instrument airLiquide() {
    return new Instrument(
        new InstrumentId(UUID.fromString("7d0f0a52-3c3e-4f4e-9d4e-0b9c3f1a2b3c")),
        new Listing(new Isin("FR0000120073"), Mic.XPAR),
        "AI",
        "Air Liquide",
        InstrumentType.STOCK,
        MarketTestData.EUR,
        Instant.parse("2026-10-19T08:00:00Z"),
        true,
        MarketTestData.UNKNOWN_BY_RULE);
  }

  /**
   * Adds a share listed on Euronext Paris to the reference data.
   *
   * @param instruments the reference data
   * @param isinBody first eleven characters of the ISIN, e.g. {@code FR000012007}
   * @param name its name
   * @param at time of the reference refresh
   * @return the stored instrument
   */
  public static Instrument stock(
      InstrumentRepository instruments, String isinBody, String name, Instant at) {
    return instrument(
        instruments,
        new Listing(MarketTestData.isin(isinBody), Mic.XPAR),
        InstrumentType.STOCK,
        name,
        at);
  }

  /**
   * Stores closes of an instrument on the sessions of a window, from an index on.
   *
   * @param prices the closes
   * @param id the instrument
   * @param window the sessions
   * @param price the close of each session index
   * @param first first session index with a close
   * @param at time of the collection
   */
  public static void saveYear(
      DailyPriceRepository prices,
      InstrumentId id,
      YearWindow window,
      IntFunction<String> price,
      int first,
      Instant at) {
    List<DailyClose> closes =
        IntStream.range(first, YearWindow.SESSIONS)
            .mapToObj(i -> close(id, window.sessions().get(i), price.apply(i)))
            .toList();
    prices.save(closes, at);
  }

  /**
   * A Euronext close with a volume of 1,000.
   *
   * @param id the instrument
   * @param day the session
   * @param price the close
   * @return the close
   */
  public static DailyClose close(InstrumentId id, LocalDate day, String price) {
    return new DailyClose(
        id, day, new BigDecimal(price), MarketTestData.EUR, PriceSource.EURONEXT, 1_000L, 10);
  }

  /**
   * Synthetic stocks on Euronext Paris.
   *
   * @param count how many
   * @return the reference entries
   */
  public static List<ListedInstrument> stocks(int count) {
    return IntStream.range(0, count)
        .mapToObj(
            i ->
                new ListedInstrument(
                    new Listing(MarketTestData.isin(String.format("FR%09d", i)), Mic.XPAR),
                    "T" + i,
                    "Synthetic " + i,
                    InstrumentType.STOCK,
                    MarketTestData.EUR))
        .toList();
  }

  /**
   * A set mixing every status: performance over one year insufficient, correlation with the CAC 40
   * not computable.
   *
   * @param asOf last session
   * @return the indicators
   */
  public static IndicatorSet sampleSet(LocalDate asOf) {
    Map<IndicatorWindow, Indicator> performance = new EnumMap<>(IndicatorWindow.class);
    performance.put(IndicatorWindow.ONE_MONTH, of("2.35"));
    performance.put(IndicatorWindow.THREE_MONTHS, of("5.10"));
    performance.put(IndicatorWindow.SIX_MONTHS, of("-1.20"));
    performance.put(IndicatorWindow.ONE_YEAR, Indicator.INSUFFICIENT_HISTORY);
    return new IndicatorSet(
        asOf,
        180,
        performance,
        of("18.40"),
        of("-12.30"),
        new BenchmarkComparison(of("3.10"), of("0.85"), of("1.05")),
        new BenchmarkComparison(of("-0.50"), Indicator.NOT_COMPUTABLE, of("0.00")),
        of("1234567.89"));
  }

  private static Indicator of(String value) {
    return Indicator.of(new BigDecimal(value));
  }

  /**
   * Inserts synthetic closes of every instrument over a window, in one statement.
   *
   * @param jdbc the database
   * @param window the sessions
   * @param at time of the collection
   * @return the number of closes inserted
   */
  public static int insertSyntheticYear(JdbcClient jdbc, YearWindow window, Instant at) {
    return jdbc.sql(SYNTHETIC_CLOSES)
        .param("at", Timestamps.of(at))
        .param("from", window.start())
        .param("to", window.asOf())
        .update();
  }
}
