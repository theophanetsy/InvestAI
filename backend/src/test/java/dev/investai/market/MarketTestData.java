package dev.investai.market;

import dev.investai.market.domain.InstrumentType;
import dev.investai.market.domain.Isin;
import dev.investai.market.domain.ListedInstrument;
import dev.investai.market.domain.Listing;
import dev.investai.market.domain.Mic;
import dev.investai.market.domain.PeaEligibility;
import dev.investai.market.domain.PeaEligibilityStatus;
import java.util.Currency;
import java.util.stream.IntStream;
import org.springframework.jdbc.core.simple.JdbcClient;

/** Synthetic market data shared by the market tests. Never real provider data. */
public final class MarketTestData {

  /** Euro. */
  public static final Currency EUR = Currency.getInstance("EUR");

  /** Eligibility of an instrument the rule cannot classify. */
  public static final PeaEligibility UNKNOWN_BY_RULE =
      PeaEligibility.byRule(PeaEligibilityStatus.UNKNOWN);

  private MarketTestData() {}

  /**
   * Builds a valid ISIN from its first eleven characters.
   *
   * @param body country code and national code, e.g. {@code FR000000001}
   * @return the ISIN with its check digit
   */
  public static Isin isin(String body) {
    return IntStream.rangeClosed(0, 9)
        .mapToObj(digit -> body + digit)
        .filter(Isin::isValid)
        .findFirst()
        .map(Isin::new)
        .orElseThrow();
  }

  /**
   * A reference entry on Euronext Paris.
   *
   * @param isin the ISIN
   * @param ticker the ticker
   * @param name the name
   * @return the entry
   */
  public static ListedInstrument listed(Isin isin, String ticker, String name) {
    return new ListedInstrument(
        new Listing(isin, Mic.XPAR), ticker, name, InstrumentType.STOCK, EUR);
  }

  /**
   * Empties the market tables: integration tests share one database.
   *
   * @param jdbc the database
   */
  public static void clearTables(JdbcClient jdbc) {
    // Literal statements only (Semgrep investai-no-string-concatenated-sql). Declared portfolio
    // lines reference instruments (portfolio module): removed first.
    jdbc.sql("DELETE FROM portfolio_line").update();
    jdbc.sql("DELETE FROM instrument_indicator").update();
    jdbc.sql("DELETE FROM price_catch_up").update();
    jdbc.sql("DELETE FROM daily_price").update();
    jdbc.sql("DELETE FROM market_collection").update();
    jdbc.sql("DELETE FROM eodhd_usage").update();
    jdbc.sql("DELETE FROM instrument").update();
  }
}
