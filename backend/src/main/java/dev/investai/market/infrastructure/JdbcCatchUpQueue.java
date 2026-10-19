package dev.investai.market.infrastructure;

import dev.investai.market.domain.CatchUpQueue;
import dev.investai.market.domain.InstrumentId;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** {@link CatchUpQueue} on the {@code price_catch_up} table. */
@Repository
class JdbcCatchUpQueue implements CatchUpQueue {

  private static final String ENQUEUE =
      """
      INSERT INTO price_catch_up (instrument_id, from_date, to_date, requested_at)
      VALUES (:instrumentId, :from, :to, :at)
      ON CONFLICT (instrument_id) DO UPDATE SET
        from_date = least(price_catch_up.from_date, excluded.from_date),
        to_date = greatest(price_catch_up.to_date, excluded.to_date)
      """;

  private final JdbcClient jdbc;
  private final NamedParameterJdbcTemplate batch;

  JdbcCatchUpQueue(JdbcClient jdbc, NamedParameterJdbcTemplate batch) {
    this.jdbc = jdbc;
    this.batch = batch;
  }

  @Override
  public void enqueue(
      Collection<InstrumentId> instruments, LocalDate from, LocalDate to, Instant requestedAt) {
    MapSqlParameterSource[] rows =
        instruments.stream()
            .map(
                instrument ->
                    new MapSqlParameterSource()
                        .addValue("instrumentId", instrument.value())
                        .addValue("from", from)
                        .addValue("to", to)
                        .addValue("at", Timestamps.of(requestedAt)))
            .toArray(MapSqlParameterSource[]::new);
    batch.batchUpdate(ENQUEUE, rows);
  }

  @Override
  public List<CatchUpRequest> pending() {
    return jdbc.sql(
            "SELECT instrument_id, from_date, to_date FROM price_catch_up"
                + " ORDER BY requested_at, instrument_id")
        .query(
            (rs, row) ->
                new CatchUpRequest(
                    new InstrumentId(rs.getObject("instrument_id", UUID.class)),
                    rs.getObject("from_date", LocalDate.class),
                    rs.getObject("to_date", LocalDate.class)))
        .list();
  }

  @Override
  public void remove(CatchUpRequest request) {
    jdbc.sql(
            "DELETE FROM price_catch_up"
                + " WHERE instrument_id = :id AND from_date = :from AND to_date = :to")
        .param("id", request.instrumentId().value())
        .param("from", request.from())
        .param("to", request.to())
        .update();
  }
}
