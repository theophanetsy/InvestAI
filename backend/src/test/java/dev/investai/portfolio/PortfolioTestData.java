package dev.investai.portfolio;

import dev.investai.market.MarketTestData;
import org.springframework.jdbc.core.simple.JdbcClient;

/** Shared helpers of the portfolio tests. */
public final class PortfolioTestData {

  private PortfolioTestData() {}

  /**
   * Empties the profile, the declared portfolio and the market tables: integration tests share one
   * database.
   *
   * @param jdbc the database
   */
  public static void clearTables(JdbcClient jdbc) {
    // Literal statements only (Semgrep investai-no-string-concatenated-sql).
    jdbc.sql("DELETE FROM investor_profile").update();
    jdbc.sql("DELETE FROM portfolio_cash").update();
    MarketTestData.clearTables(jdbc);
  }
}
