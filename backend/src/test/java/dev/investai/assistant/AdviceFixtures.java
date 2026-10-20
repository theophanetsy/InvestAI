package dev.investai.assistant;

import dev.investai.market.IndicatorFixtures;
import dev.investai.market.MarketTestData;
import dev.investai.market.domain.EuronextCalendar;
import dev.investai.market.domain.Instrument;
import dev.investai.market.domain.InstrumentId;
import dev.investai.market.domain.InstrumentRepository;
import dev.investai.market.domain.InstrumentType;
import dev.investai.market.domain.Isin;
import dev.investai.market.domain.ListedInstrument;
import dev.investai.market.domain.Listing;
import dev.investai.market.domain.Mic;
import dev.investai.market.domain.YearWindow;
import dev.investai.support.McpTestClient;
import java.io.IOException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.IntStream;
import tools.jackson.databind.JsonNode;

/**
 * Synthetic data of the tests of the advice tools (spec 3.1): reference data of the configured
 * benchmarks, instruments and the call of a tool. Never real market data.
 */
public final class AdviceFixtures {

  /** Session of the synthetic closes and indicators. */
  public static final LocalDate FRIDAY = LocalDate.parse("2026-10-16");

  /** Time of the synthetic computation. */
  public static final Instant COMPUTED_AT = Instant.parse("2026-10-17T07:00:00Z");

  /** Amundi MSCI World (CW8), in {@code investai.market.benchmarks} and the indicators. */
  public static final String CW8 = "LU1681043599";

  /** Amundi CAC 40 (CAC), in {@code investai.market.benchmarks} and the indicators. */
  public static final String CAC = "FR0007052782";

  /** iShares MSCI World Swap PEA (WPEA), in {@code investai.market.benchmarks}. */
  public static final String WPEA = "IE0002XZSHO1";

  private AdviceFixtures() {}

  /**
   * Adds a listing of Euronext Paris to the reference data.
   *
   * @param instruments the reference data
   * @param isin a full ISIN
   * @param type kind of instrument
   * @param name its name
   * @return the stored instrument
   */
  public static Instrument listed(
      InstrumentRepository instruments, String isin, InstrumentType type, String name) {
    return IndicatorFixtures.instrument(
        instruments, new Listing(new Isin(isin), Mic.XPAR), type, name, COMPUTED_AT);
  }

  /**
   * Adds a share of a country outside the EEA: not eligible to the PEA by the rule.
   *
   * @param instruments the reference data
   * @param name its name
   * @return the stored instrument
   */
  public static Instrument foreignStock(InstrumentRepository instruments, String name) {
    return IndicatorFixtures.instrument(
        instruments,
        new Listing(MarketTestData.isin("US000000001"), Mic.XPAR),
        InstrumentType.STOCK,
        name,
        COMPUTED_AT);
  }

  /**
   * Adds shares and ETFs of Euronext Paris whose texts are the longest the database stores: names
   * of 256 characters starting with {@code x}, tickers of 32.
   *
   * @param instruments the reference data
   * @param count how many
   * @param wide the character repeated in the texts
   * @return the stored instruments
   */
  public static List<Instrument> wideInstruments(
      InstrumentRepository instruments, int count, String wide) {
    instruments.upsertReference(
        IntStream.range(0, count)
            .mapToObj(
                i ->
                    new ListedInstrument(
                        new Listing(MarketTestData.isin(String.format("FR%09d", i)), Mic.XPAR),
                        wide.repeat(32),
                        "x" + wide.repeat(255),
                        i % 2 == 0 ? InstrumentType.STOCK : InstrumentType.ETF,
                        MarketTestData.EUR))
            .toList(),
        COMPUTED_AT);
    return instruments.search("x", count);
  }

  /**
   * The year of sessions ending on {@link #FRIDAY}.
   *
   * @return the window
   */
  public static YearWindow year() {
    return YearWindow.ending(FRIDAY, new EuronextCalendar());
  }

  /**
   * Calls a tool and returns its result.
   *
   * @param mcp the client
   * @param tool its name
   * @param arguments its arguments, as JSON
   * @return the {@code result} member
   * @throws IOException on network errors
   * @throws InterruptedException when interrupted
   */
  public static JsonNode call(McpTestClient mcp, String tool, String arguments)
      throws IOException, InterruptedException {
    return mcp.result("tools/call", "{\"name\":\"" + tool + "\",\"arguments\":" + arguments + "}");
  }

  /**
   * The arguments of a tool taking one instrument.
   *
   * @param id the instrument
   * @return the JSON arguments
   */
  public static String instrument(InstrumentId id) {
    return "{\"instrumentId\":\"" + id + "\"}";
  }
}
