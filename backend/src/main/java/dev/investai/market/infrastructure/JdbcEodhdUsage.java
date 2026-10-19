package dev.investai.market.infrastructure;

import dev.investai.market.domain.ApiCallCounter;
import java.time.LocalDate;
import java.util.OptionalInt;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * {@link ApiCallCounter} of EODHD on the {@code eodhd_usage} table. The reservation is a single
 * statement: two concurrent jobs can never exceed the limit.
 */
@Repository
class JdbcEodhdUsage implements ApiCallCounter {

  private static final String RESERVE =
      """
      INSERT INTO eodhd_usage (usage_date, calls) VALUES (:day, 1)
      ON CONFLICT (usage_date) DO UPDATE SET calls = eodhd_usage.calls + 1
      WHERE eodhd_usage.calls < :limit
      RETURNING calls
      """;

  private final JdbcClient jdbc;

  JdbcEodhdUsage(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public OptionalInt reserve(LocalDate day, int limit) {
    if (limit < 1) {
      return OptionalInt.empty();
    }
    return jdbc.sql(RESERVE)
        .param("day", day)
        .param("limit", limit)
        .query(Integer.class)
        .optional()
        .map(OptionalInt::of)
        .orElseGet(OptionalInt::empty);
  }
}
