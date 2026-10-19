package dev.investai.market.infrastructure;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.jspecify.annotations.Nullable;

/**
 * {@code timestamptz} conversions: the PostgreSQL driver binds {@link OffsetDateTime}, not {@link
 * Instant}.
 */
final class Timestamps {

  private Timestamps() {}

  static OffsetDateTime of(Instant instant) {
    return instant.atOffset(ZoneOffset.UTC);
  }

  static @Nullable Instant nullable(ResultSet rs, String column) throws SQLException {
    OffsetDateTime value = rs.getObject(column, OffsetDateTime.class);
    return value == null ? null : value.toInstant();
  }
}
