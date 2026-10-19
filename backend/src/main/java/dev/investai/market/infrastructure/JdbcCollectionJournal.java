package dev.investai.market.infrastructure;

import dev.investai.market.domain.CollectionJournal;
import dev.investai.market.domain.TradeFileSegment;
import java.sql.Date;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** {@link CollectionJournal} on the {@code market_collection} table. */
@Repository
class JdbcCollectionJournal implements CollectionJournal {

  private static final String RECORD_FILE =
      """
      INSERT INTO market_collection (id, trading_date, segment, source, processed_at, closes_count)
      VALUES (:id, :session, :segment, 'EURONEXT', :at, :closes)
      ON CONFLICT ON CONSTRAINT market_collection_session_key DO UPDATE SET
        processed_at = excluded.processed_at, closes_count = excluded.closes_count
      """;

  private static final String RECORD_CATCH_UP =
      """
      INSERT INTO market_collection (id, trading_date, segment, source, processed_at, closes_count)
      VALUES (:id, :session, NULL, 'EODHD', :at, 0)
      ON CONFLICT ON CONSTRAINT market_collection_session_key DO NOTHING
      """;

  private static final String REFRESH_COUNTS =
      """
      UPDATE market_collection c SET closes_count =
        (SELECT count(*) FROM daily_price p
         WHERE p.trading_date = c.trading_date AND p.source = 'EODHD')
      WHERE c.source = 'EODHD' AND c.trading_date BETWEEN :from AND :to
      """;

  /** Complete: caught up (segment null), or one row per segment. */
  private static final String CATCH_UP_START =
      """
      SELECT coalesce(
        (SELECT max(trading_date) + 1 FROM (
           SELECT trading_date FROM market_collection WHERE trading_date < :session
           GROUP BY trading_date
           HAVING bool_or(segment IS NULL) OR count(DISTINCT segment) = :segments) complete),
        (SELECT min(trading_date) FROM market_collection WHERE trading_date < :session))
      """;

  private static final String SESSION = "session";

  private final JdbcClient jdbc;
  private final NamedParameterJdbcTemplate batch;

  JdbcCollectionJournal(JdbcClient jdbc, NamedParameterJdbcTemplate batch) {
    this.jdbc = jdbc;
    this.batch = batch;
  }

  @Override
  public boolean isCollected(LocalDate session, TradeFileSegment segment) {
    return jdbc.sql(
            "SELECT EXISTS (SELECT 1 FROM market_collection WHERE trading_date = :session"
                + " AND (segment = :segment OR segment IS NULL))")
        .param(SESSION, session)
        .param("segment", segment.name())
        .query(Boolean.class)
        .single();
  }

  @Override
  public void recordFile(
      LocalDate session, TradeFileSegment segment, int closes, Instant processedAt) {
    jdbc.sql(RECORD_FILE)
        .param("id", UUID.randomUUID())
        .param(SESSION, session)
        .param("segment", segment.name())
        .param("at", Timestamps.of(processedAt))
        .param("closes", closes)
        .update();
  }

  @Override
  public void recordCatchUp(Collection<LocalDate> sessions, Instant processedAt) {
    MapSqlParameterSource[] rows =
        sessions.stream()
            .map(
                session ->
                    new MapSqlParameterSource()
                        .addValue("id", UUID.randomUUID())
                        .addValue(SESSION, session)
                        .addValue("at", Timestamps.of(processedAt)))
            .toArray(MapSqlParameterSource[]::new);
    batch.batchUpdate(RECORD_CATCH_UP, rows);
  }

  @Override
  public void refreshCatchUpCounts(LocalDate from, LocalDate to) {
    jdbc.sql(REFRESH_COUNTS).param("from", from).param("to", to).update();
  }

  @Override
  public Optional<LocalDate> catchUpStart(LocalDate session) {
    return jdbc.sql(CATCH_UP_START)
        .param(SESSION, session)
        .param("segments", TradeFileSegment.values().length)
        .query((rs, row) -> Optional.ofNullable(rs.getDate(1)))
        .single()
        .map(Date::toLocalDate);
  }
}
