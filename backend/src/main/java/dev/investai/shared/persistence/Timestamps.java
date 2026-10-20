package dev.investai.shared.persistence;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.jspecify.annotations.Nullable;

/**
 * {@code timestamptz} conversions: the PostgreSQL driver binds {@link OffsetDateTime}, not {@link
 * Instant}. Shared by the JDBC adapters of every module.
 */
public final class Timestamps {

  private Timestamps() {}

  /**
   * Converts an instant to bind it.
   *
   * @param instant the instant
   * @return the same instant at UTC
   */
  public static OffsetDateTime of(Instant instant) {
    return instant.atOffset(ZoneOffset.UTC);
  }

  /**
   * Reads a nullable {@code timestamptz} column.
   *
   * @param rs the current row
   * @param column the column
   * @return the instant, {@code null} when the column is null
   * @throws SQLException when the column cannot be read
   */
  public static @Nullable Instant nullable(ResultSet rs, String column) throws SQLException {
    OffsetDateTime value = rs.getObject(column, OffsetDateTime.class);
    return value == null ? null : value.toInstant();
  }
}
