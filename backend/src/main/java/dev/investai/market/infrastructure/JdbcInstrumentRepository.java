package dev.investai.market.infrastructure;

import dev.investai.market.domain.Instrument;
import dev.investai.market.domain.InstrumentId;
import dev.investai.market.domain.InstrumentRepository;
import dev.investai.market.domain.InstrumentType;
import dev.investai.market.domain.Isin;
import dev.investai.market.domain.ListedInstrument;
import dev.investai.market.domain.Listing;
import dev.investai.market.domain.Mic;
import dev.investai.market.domain.SearchText;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Currency;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** {@link InstrumentRepository} on the {@code instrument} table. */
@Repository
class JdbcInstrumentRepository implements InstrumentRepository {

  private static final String SELECT =
      "SELECT id, isin, mic, ticker, name, type, currency, tracked_since, history_loaded"
          + " FROM instrument ";

  private static final String UPSERT =
      """
      INSERT INTO instrument (id, isin, mic, ticker, name, name_search, type, currency,
                              reference_seen_at, created_at, updated_at)
      VALUES (:id, :isin, :mic, :ticker, :name, :nameSearch, :type, :currency,
              :seenAt, :seenAt, :seenAt)
      ON CONFLICT (isin, mic) DO UPDATE SET
        ticker = excluded.ticker, name = excluded.name, name_search = excluded.name_search,
        type = excluded.type, currency = excluded.currency,
        reference_seen_at = excluded.reference_seen_at, updated_at = excluded.updated_at
      """;

  private static final String SEARCH =
      SELECT
          + """
          WHERE isin = :exact OR upper(ticker) = :exact OR name_search LIKE :fragment ESCAPE '\\'
          ORDER BY tracked_since IS NULL, name_search, isin
          LIMIT :limit
          """;

  private static final String ID = "id";

  private final JdbcClient jdbc;
  private final NamedParameterJdbcTemplate batch;

  JdbcInstrumentRepository(JdbcClient jdbc, NamedParameterJdbcTemplate batch) {
    this.jdbc = jdbc;
    this.batch = batch;
  }

  @Override
  public Optional<Instrument> findById(InstrumentId id) {
    return jdbc.sql(SELECT + "WHERE id = :id")
        .param(ID, id.value())
        .query(JdbcInstrumentRepository::map)
        .optional();
  }

  @Override
  public Optional<Instrument> findByListing(Listing listing) {
    return jdbc.sql(SELECT + "WHERE isin = :isin AND mic = :mic")
        .param("isin", listing.isin().value())
        .param("mic", listing.mic().value())
        .query(JdbcInstrumentRepository::map)
        .optional();
  }

  @Override
  public List<Instrument> findTracked() {
    return jdbc.sql(SELECT + "WHERE tracked_since IS NOT NULL ORDER BY tracked_since, isin, mic")
        .query(JdbcInstrumentRepository::map)
        .list();
  }

  @Override
  public List<Instrument> search(String query, int limit) {
    String fragment = SearchText.normalize(query);
    return jdbc.sql(SEARCH)
        .param("exact", query.strip().toUpperCase(Locale.ROOT))
        .param("fragment", "%" + escapeLike(fragment) + "%")
        .param("limit", limit)
        .query(JdbcInstrumentRepository::map)
        .list();
  }

  @Override
  public void upsertReference(List<ListedInstrument> instruments, Instant seenAt) {
    OffsetDateTime at = Timestamps.of(seenAt);
    MapSqlParameterSource[] rows =
        instruments.stream()
            .map(
                instrument ->
                    new MapSqlParameterSource()
                        .addValue(ID, UUID.randomUUID())
                        .addValue("isin", instrument.listing().isin().value())
                        .addValue("mic", instrument.listing().mic().value())
                        .addValue("ticker", instrument.ticker())
                        .addValue("name", instrument.name())
                        .addValue("nameSearch", SearchText.normalize(instrument.name()))
                        .addValue("type", instrument.type().name())
                        .addValue("currency", instrument.currency().getCurrencyCode())
                        .addValue("seenAt", at))
            .toArray(MapSqlParameterSource[]::new);
    batch.batchUpdate(UPSERT, rows);
  }

  @Override
  public boolean markTracked(InstrumentId id, Instant since) {
    return jdbc.sql(
                "UPDATE instrument SET tracked_since = :since, updated_at = :since"
                    + " WHERE id = :id AND tracked_since IS NULL")
            .param(ID, id.value())
            .param("since", Timestamps.of(since))
            .update()
        == 1;
  }

  @Override
  public void markHistoryLoaded(InstrumentId id, Instant at) {
    jdbc.sql("UPDATE instrument SET history_loaded = true, updated_at = :at WHERE id = :id")
        .param(ID, id.value())
        .param("at", Timestamps.of(at))
        .update();
  }

  @Override
  public Optional<Instant> lastReferenceRefresh() {
    return jdbc.sql("SELECT max(reference_seen_at) FROM instrument")
        .query((rs, row) -> Optional.ofNullable(rs.getObject(1, OffsetDateTime.class)))
        .single()
        .map(OffsetDateTime::toInstant);
  }

  /** LIKE wildcards typed by the user are searched literally. */
  private static String escapeLike(String text) {
    return text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
  }

  private static Instrument map(ResultSet rs, int row) throws SQLException {
    return new Instrument(
        new InstrumentId(rs.getObject(ID, UUID.class)),
        new Listing(new Isin(rs.getString("isin")), new Mic(rs.getString("mic"))),
        rs.getString("ticker"),
        rs.getString("name"),
        InstrumentType.valueOf(rs.getString("type")),
        Currency.getInstance(rs.getString("currency")),
        Timestamps.nullable(rs, "tracked_since"),
        rs.getBoolean("history_loaded"));
  }
}
