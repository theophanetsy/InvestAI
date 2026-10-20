package dev.investai.market.infrastructure;

import dev.investai.market.domain.BenchmarkComparison;
import dev.investai.market.domain.Indicator;
import dev.investai.market.domain.IndicatorRepository;
import dev.investai.market.domain.IndicatorRun;
import dev.investai.market.domain.IndicatorSet;
import dev.investai.market.domain.IndicatorStatus;
import dev.investai.market.domain.IndicatorWindow;
import dev.investai.market.domain.InstrumentId;
import dev.investai.market.domain.StoredIndicators;
import dev.investai.shared.persistence.Timestamps;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@link IndicatorRepository} on the {@code instrument_indicator} table: one row per instrument,
 * each indicator stored as a value and a status.
 */
@Repository
class JdbcIndicatorRepository implements IndicatorRepository {

  // One literal per query (Semgrep investai-no-string-concatenated-sql): the column list is
  // repeated.
  private static final String FIND_BY_ID =
      """
      SELECT instrument_id, as_of, computed_at, closes_one_year,
             performance_one_month, performance_one_month_status,
             performance_three_months, performance_three_months_status,
             performance_six_months, performance_six_months_status,
             performance_one_year, performance_one_year_status,
             volatility, volatility_status, max_drawdown, max_drawdown_status,
             msci_world_outperformance, msci_world_outperformance_status,
             msci_world_correlation, msci_world_correlation_status,
             msci_world_beta, msci_world_beta_status,
             cac40_outperformance, cac40_outperformance_status,
             cac40_correlation, cac40_correlation_status, cac40_beta, cac40_beta_status,
             average_daily_traded_amount, average_daily_traded_amount_status
      FROM instrument_indicator WHERE instrument_id = :id
      """;

  private static final String FIND_ALL =
      """
      SELECT instrument_id, as_of, computed_at, closes_one_year,
             performance_one_month, performance_one_month_status,
             performance_three_months, performance_three_months_status,
             performance_six_months, performance_six_months_status,
             performance_one_year, performance_one_year_status,
             volatility, volatility_status, max_drawdown, max_drawdown_status,
             msci_world_outperformance, msci_world_outperformance_status,
             msci_world_correlation, msci_world_correlation_status,
             msci_world_beta, msci_world_beta_status,
             cac40_outperformance, cac40_outperformance_status,
             cac40_correlation, cac40_correlation_status, cac40_beta, cac40_beta_status,
             average_daily_traded_amount, average_daily_traded_amount_status
      FROM instrument_indicator
      """;

  private static final String LAST_RUN =
      """
      SELECT as_of, computed_at FROM instrument_indicator
      ORDER BY computed_at DESC, as_of DESC LIMIT 1
      """;

  private static final String UPSERT =
      """
      INSERT INTO instrument_indicator (
        instrument_id, as_of, computed_at, closes_one_year,
        performance_one_month, performance_one_month_status,
        performance_three_months, performance_three_months_status,
        performance_six_months, performance_six_months_status,
        performance_one_year, performance_one_year_status,
        volatility, volatility_status, max_drawdown, max_drawdown_status,
        msci_world_outperformance, msci_world_outperformance_status,
        msci_world_correlation, msci_world_correlation_status,
        msci_world_beta, msci_world_beta_status,
        cac40_outperformance, cac40_outperformance_status,
        cac40_correlation, cac40_correlation_status, cac40_beta, cac40_beta_status,
        average_daily_traded_amount, average_daily_traded_amount_status)
      VALUES (
        :instrument_id, :as_of, :computed_at, :closes_one_year,
        :performance_one_month, :performance_one_month_status,
        :performance_three_months, :performance_three_months_status,
        :performance_six_months, :performance_six_months_status,
        :performance_one_year, :performance_one_year_status,
        :volatility, :volatility_status, :max_drawdown, :max_drawdown_status,
        :msci_world_outperformance, :msci_world_outperformance_status,
        :msci_world_correlation, :msci_world_correlation_status,
        :msci_world_beta, :msci_world_beta_status,
        :cac40_outperformance, :cac40_outperformance_status,
        :cac40_correlation, :cac40_correlation_status, :cac40_beta, :cac40_beta_status,
        :average_daily_traded_amount, :average_daily_traded_amount_status)
      ON CONFLICT (instrument_id) DO UPDATE SET
        as_of = excluded.as_of, computed_at = excluded.computed_at,
        closes_one_year = excluded.closes_one_year,
        performance_one_month = excluded.performance_one_month,
        performance_one_month_status = excluded.performance_one_month_status,
        performance_three_months = excluded.performance_three_months,
        performance_three_months_status = excluded.performance_three_months_status,
        performance_six_months = excluded.performance_six_months,
        performance_six_months_status = excluded.performance_six_months_status,
        performance_one_year = excluded.performance_one_year,
        performance_one_year_status = excluded.performance_one_year_status,
        volatility = excluded.volatility, volatility_status = excluded.volatility_status,
        max_drawdown = excluded.max_drawdown, max_drawdown_status = excluded.max_drawdown_status,
        msci_world_outperformance = excluded.msci_world_outperformance,
        msci_world_outperformance_status = excluded.msci_world_outperformance_status,
        msci_world_correlation = excluded.msci_world_correlation,
        msci_world_correlation_status = excluded.msci_world_correlation_status,
        msci_world_beta = excluded.msci_world_beta,
        msci_world_beta_status = excluded.msci_world_beta_status,
        cac40_outperformance = excluded.cac40_outperformance,
        cac40_outperformance_status = excluded.cac40_outperformance_status,
        cac40_correlation = excluded.cac40_correlation,
        cac40_correlation_status = excluded.cac40_correlation_status,
        cac40_beta = excluded.cac40_beta, cac40_beta_status = excluded.cac40_beta_status,
        average_daily_traded_amount = excluded.average_daily_traded_amount,
        average_daily_traded_amount_status = excluded.average_daily_traded_amount_status
      """;

  private static final Map<IndicatorWindow, String> PERFORMANCE_COLUMNS =
      Map.of(
          IndicatorWindow.ONE_MONTH, "performance_one_month",
          IndicatorWindow.THREE_MONTHS, "performance_three_months",
          IndicatorWindow.SIX_MONTHS, "performance_six_months",
          IndicatorWindow.ONE_YEAR, "performance_one_year");

  private static final String STATUS_SUFFIX = "_status";
  private static final String INSTRUMENT_ID = "instrument_id";
  private static final String VOLATILITY = "volatility";
  private static final String MAX_DRAWDOWN = "max_drawdown";
  private static final String AVERAGE_DAILY_TRADED_AMOUNT = "average_daily_traded_amount";
  private static final String MSCI_WORLD = "msci_world";
  private static final String CAC40 = "cac40";

  private final JdbcClient jdbc;
  private final NamedParameterJdbcTemplate batch;

  JdbcIndicatorRepository(JdbcClient jdbc, NamedParameterJdbcTemplate batch) {
    this.jdbc = jdbc;
    this.batch = batch;
  }

  @Override
  @Transactional
  public void saveAll(Map<InstrumentId, IndicatorSet> indicators, Instant computedAt) {
    OffsetDateTime at = Timestamps.of(computedAt);
    MapSqlParameterSource[] rows =
        indicators.entrySet().stream()
            .map(entry -> parameters(entry.getKey(), entry.getValue(), at))
            .toArray(MapSqlParameterSource[]::new);
    batch.batchUpdate(UPSERT, rows);
  }

  @Override
  public Optional<StoredIndicators> find(InstrumentId id) {
    return jdbc.sql(FIND_BY_ID).param("id", id.value()).query((rs, row) -> map(rs)).optional();
  }

  @Override
  public List<StoredIndicators> findAll() {
    return jdbc.sql(FIND_ALL).query((rs, row) -> map(rs)).list();
  }

  @Override
  public Optional<IndicatorRun> lastRun() {
    return jdbc.sql(LAST_RUN)
        .query(
            (rs, row) ->
                new IndicatorRun(
                    rs.getObject("as_of", LocalDate.class),
                    rs.getObject("computed_at", OffsetDateTime.class).toInstant()))
        .optional();
  }

  private static MapSqlParameterSource parameters(
      InstrumentId id, IndicatorSet set, OffsetDateTime computedAt) {
    MapSqlParameterSource row =
        new MapSqlParameterSource()
            .addValue(INSTRUMENT_ID, id.value())
            .addValue("as_of", set.asOf())
            .addValue("computed_at", computedAt)
            .addValue("closes_one_year", set.closesOneYear());
    PERFORMANCE_COLUMNS.forEach(
        (window, column) -> addIndicator(row, column, set.performance(window)));
    addIndicator(row, VOLATILITY, set.volatility());
    addIndicator(row, MAX_DRAWDOWN, set.maxDrawdown());
    addComparison(row, MSCI_WORLD, set.msciWorld());
    addComparison(row, CAC40, set.cac40());
    addIndicator(row, AVERAGE_DAILY_TRADED_AMOUNT, set.averageDailyTradedAmount());
    return row;
  }

  private static void addComparison(
      MapSqlParameterSource row, String benchmark, BenchmarkComparison comparison) {
    addIndicator(row, String.join("_", benchmark, "outperformance"), comparison.outperformance());
    addIndicator(row, String.join("_", benchmark, "correlation"), comparison.correlation());
    addIndicator(row, String.join("_", benchmark, "beta"), comparison.beta());
  }

  private static void addIndicator(MapSqlParameterSource row, String column, Indicator indicator) {
    row.addValue(column, indicator.value(), Types.NUMERIC)
        .addValue(column + STATUS_SUFFIX, indicator.status().name());
  }

  private static StoredIndicators map(ResultSet rs) throws SQLException {
    Map<IndicatorWindow, Indicator> performance = new EnumMap<>(IndicatorWindow.class);
    for (Map.Entry<IndicatorWindow, String> column : PERFORMANCE_COLUMNS.entrySet()) {
      performance.put(column.getKey(), indicator(rs, column.getValue()));
    }
    IndicatorSet set =
        new IndicatorSet(
            rs.getObject("as_of", LocalDate.class),
            rs.getInt("closes_one_year"),
            performance,
            indicator(rs, VOLATILITY),
            indicator(rs, MAX_DRAWDOWN),
            comparison(rs, MSCI_WORLD),
            comparison(rs, CAC40),
            indicator(rs, AVERAGE_DAILY_TRADED_AMOUNT));
    return new StoredIndicators(
        new InstrumentId(rs.getObject(INSTRUMENT_ID, UUID.class)),
        set,
        rs.getObject("computed_at", OffsetDateTime.class).toInstant());
  }

  private static BenchmarkComparison comparison(ResultSet rs, String benchmark)
      throws SQLException {
    return new BenchmarkComparison(
        indicator(rs, String.join("_", benchmark, "outperformance")),
        indicator(rs, String.join("_", benchmark, "correlation")),
        indicator(rs, String.join("_", benchmark, "beta")));
  }

  private static Indicator indicator(ResultSet rs, String column) throws SQLException {
    return new Indicator(
        IndicatorStatus.valueOf(rs.getString(column + STATUS_SUFFIX)), rs.getBigDecimal(column));
  }
}
