package dev.investai.market.infrastructure;

import dev.investai.market.domain.DailyClose;
import dev.investai.market.domain.DailyPriceRepository;
import dev.investai.market.domain.InstrumentId;
import dev.investai.market.domain.PriceKey;
import dev.investai.market.domain.PriceSource;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.Currency;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** {@link DailyPriceRepository} on the {@code daily_price} table. */
@Repository
class JdbcDailyPriceRepository implements DailyPriceRepository {

  // One literal per query (Semgrep investai-no-string-concatenated-sql): the column list is
  // repeated.
  private static final String FIND =
      """
      SELECT instrument_id, trading_date, close, currency, source FROM daily_price
      WHERE instrument_id IN (:ids) AND trading_date BETWEEN :from AND :to
      """;

  private static final String FIND_BETWEEN =
      """
      SELECT instrument_id, trading_date, close, currency, source FROM daily_price
      WHERE instrument_id = :instrument AND trading_date BETWEEN :from AND :to
      ORDER BY trading_date
      """;

  private static final String FIND_LATEST =
      """
      SELECT instrument_id, trading_date, close, currency, source FROM daily_price
      WHERE instrument_id = :instrument ORDER BY trading_date DESC LIMIT 1
      """;

  /** The WHERE clause keeps the source priority even if a caller forgot {@code PriceMerge}. */
  private static final String UPSERT =
      """
      INSERT INTO daily_price (instrument_id, trading_date, close, currency, source, collected_at)
      VALUES (:instrumentId, :tradingDate, :close, :currency, :source, :collectedAt)
      ON CONFLICT (instrument_id, trading_date) DO UPDATE SET
        close = excluded.close, currency = excluded.currency, source = excluded.source,
        collected_at = excluded.collected_at
      WHERE NOT (daily_price.source = 'EURONEXT' AND excluded.source = 'EODHD')
      """;

  private static final String INSTRUMENT = "instrument";
  private static final String FROM = "from";
  private static final String TO = "to";

  private final JdbcClient jdbc;
  private final NamedParameterJdbcTemplate batch;

  JdbcDailyPriceRepository(JdbcClient jdbc, NamedParameterJdbcTemplate batch) {
    this.jdbc = jdbc;
    this.batch = batch;
  }

  @Override
  public Map<PriceKey, DailyClose> find(
      Collection<InstrumentId> instruments, LocalDate from, LocalDate to) {
    if (instruments.isEmpty()) {
      return Map.of();
    }
    List<UUID> ids = instruments.stream().map(InstrumentId::value).toList();
    return jdbc
        .sql(FIND)
        .param("ids", ids)
        .param(FROM, from)
        .param(TO, to)
        .query((rs, row) -> map(rs))
        .list()
        .stream()
        .collect(Collectors.toMap(DailyClose::key, Function.identity()));
  }

  @Override
  public void save(Collection<DailyClose> closes, Instant collectedAt) {
    OffsetDateTime at = Timestamps.of(collectedAt);
    MapSqlParameterSource[] rows =
        closes.stream()
            .map(
                close ->
                    new MapSqlParameterSource()
                        .addValue("instrumentId", close.instrumentId().value())
                        .addValue("tradingDate", close.tradingDate())
                        .addValue("close", close.close())
                        .addValue("currency", close.currency().getCurrencyCode())
                        .addValue("source", close.source().name())
                        .addValue("collectedAt", at))
            .toArray(MapSqlParameterSource[]::new);
    batch.batchUpdate(UPSERT, rows);
  }

  @Override
  public List<DailyClose> findBetween(InstrumentId instrument, LocalDate from, LocalDate to) {
    return jdbc.sql(FIND_BETWEEN)
        .param(INSTRUMENT, instrument.value())
        .param(FROM, from)
        .param(TO, to)
        .query((rs, row) -> map(rs))
        .list();
  }

  @Override
  public Optional<DailyClose> findLatest(InstrumentId instrument) {
    return jdbc.sql(FIND_LATEST)
        .param(INSTRUMENT, instrument.value())
        .query((rs, row) -> map(rs))
        .optional();
  }

  @Override
  public int count(LocalDate session, PriceSource source) {
    return jdbc.sql(
            "SELECT count(*) FROM daily_price WHERE trading_date = :session AND source = :source")
        .param("session", session)
        .param("source", source.name())
        .query(Integer.class)
        .single();
  }

  private static DailyClose map(ResultSet rs) throws SQLException {
    return new DailyClose(
        new InstrumentId(rs.getObject("instrument_id", UUID.class)),
        rs.getObject("trading_date", LocalDate.class),
        rs.getBigDecimal("close"),
        Currency.getInstance(rs.getString("currency")),
        PriceSource.valueOf(rs.getString("source")));
  }
}
