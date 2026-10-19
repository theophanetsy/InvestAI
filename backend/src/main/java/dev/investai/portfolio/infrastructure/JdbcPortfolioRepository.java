package dev.investai.portfolio.infrastructure;

import dev.investai.market.domain.InstrumentId;
import dev.investai.portfolio.domain.Amounts;
import dev.investai.portfolio.domain.Cash;
import dev.investai.portfolio.domain.PortfolioLine;
import dev.investai.portfolio.domain.PortfolioLineId;
import dev.investai.portfolio.domain.PortfolioRepository;
import dev.investai.shared.persistence.Timestamps;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * {@link PortfolioRepository} on {@code portfolio_line} and the single row of {@code
 * portfolio_cash}.
 */
@Repository
class JdbcPortfolioRepository implements PortfolioRepository {

  private static final String FIND_LINES =
      """
      SELECT id, instrument_id, quantity, average_price, updated_at FROM portfolio_line
      """;

  private static final String FIND_LINE =
      """
      SELECT id, instrument_id, quantity, average_price, updated_at FROM portfolio_line
      WHERE id = :id
      """;

  /** The unique key on the instrument makes the duplicate check atomic. */
  private static final String INSERT_LINE =
      """
      INSERT INTO portfolio_line (id, instrument_id, quantity, average_price, currency,
                                  created_at, updated_at)
      VALUES (:id, :instrument, :quantity, :averagePrice, :currency, :at, :at)
      ON CONFLICT (instrument_id) DO NOTHING
      """;

  private static final String UPDATE_LINE =
      """
      UPDATE portfolio_line
      SET quantity = :quantity, average_price = :averagePrice, updated_at = :at
      WHERE id = :id
      """;

  private static final String UPSERT_CASH =
      """
      INSERT INTO portfolio_cash (singleton, amount, currency, updated_at)
      VALUES (true, :amount, :currency, :at)
      ON CONFLICT (singleton) DO UPDATE SET
        amount = excluded.amount, currency = excluded.currency, updated_at = excluded.updated_at
      """;

  private static final String ID = "id";
  private static final String QUANTITY = "quantity";
  private static final String AVERAGE_PRICE = "averagePrice";
  private static final String AT = "at";

  private final JdbcClient jdbc;

  JdbcPortfolioRepository(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public List<PortfolioLine> findLines() {
    return jdbc.sql(FIND_LINES).query((rs, row) -> map(rs)).list();
  }

  @Override
  public Optional<PortfolioLine> findLine(PortfolioLineId id) {
    return jdbc.sql(FIND_LINE).param(ID, id.value()).query((rs, row) -> map(rs)).optional();
  }

  @Override
  public boolean insert(PortfolioLine line) {
    return jdbc.sql(INSERT_LINE)
            .param(ID, line.id().value())
            .param("instrument", line.instrumentId().value())
            .param(QUANTITY, line.quantity())
            .param(AVERAGE_PRICE, line.averagePrice(), Types.NUMERIC)
            .param("currency", Amounts.EUR.getCurrencyCode())
            .param(AT, Timestamps.of(line.updatedAt()))
            .update()
        == 1;
  }

  @Override
  public boolean update(PortfolioLine line) {
    return jdbc.sql(UPDATE_LINE)
            .param(ID, line.id().value())
            .param(QUANTITY, line.quantity())
            .param(AVERAGE_PRICE, line.averagePrice(), Types.NUMERIC)
            .param(AT, Timestamps.of(line.updatedAt()))
            .update()
        == 1;
  }

  @Override
  public boolean delete(PortfolioLineId id) {
    return jdbc.sql("DELETE FROM portfolio_line WHERE id = :id").param(ID, id.value()).update()
        == 1;
  }

  @Override
  public Optional<Cash> findCash() {
    return jdbc.sql("SELECT amount, updated_at FROM portfolio_cash")
        .query((rs, row) -> new Cash(rs.getBigDecimal("amount"), updatedAt(rs)))
        .optional();
  }

  @Override
  public void saveCash(Cash cash) {
    jdbc.sql(UPSERT_CASH)
        .param("amount", cash.amount())
        .param("currency", Amounts.EUR.getCurrencyCode())
        .param(AT, Timestamps.of(cash.updatedAt()))
        .update();
  }

  private static PortfolioLine map(ResultSet rs) throws SQLException {
    return new PortfolioLine(
        new PortfolioLineId(rs.getObject(ID, UUID.class)),
        new InstrumentId(rs.getObject("instrument_id", UUID.class)),
        rs.getLong(QUANTITY),
        rs.getBigDecimal("average_price"),
        updatedAt(rs));
  }

  private static Instant updatedAt(ResultSet rs) throws SQLException {
    return rs.getObject("updated_at", OffsetDateTime.class).toInstant();
  }
}
