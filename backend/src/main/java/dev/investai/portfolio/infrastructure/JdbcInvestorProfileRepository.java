package dev.investai.portfolio.infrastructure;

import dev.investai.portfolio.domain.Amounts;
import dev.investai.portfolio.domain.InvestorProfile;
import dev.investai.portfolio.domain.InvestorProfileRepository;
import dev.investai.portfolio.domain.RiskProfile;
import dev.investai.shared.persistence.Timestamps;
import java.sql.Array;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * {@link InvestorProfileRepository} on the single row of {@code investor_profile} (same JDBC
 * conventions as the market module: one SQL text block per query, upsert {@code ON CONFLICT}).
 */
@Repository
class JdbcInvestorProfileRepository implements InvestorProfileRepository {

  private static final String FIND =
      """
      SELECT amount_to_invest, horizon_years, risk_profile, exclusions, objectives,
             pea_opened_on, updated_at
      FROM investor_profile
      """;

  private static final String UPSERT =
      """
      INSERT INTO investor_profile (singleton, amount_to_invest, currency, horizon_years,
                                    risk_profile, exclusions, objectives, pea_opened_on,
                                    updated_at)
      VALUES (true, :amount, :currency, :horizon, :risk, :exclusions, :objectives, :openedOn, :at)
      ON CONFLICT (singleton) DO UPDATE SET
        amount_to_invest = excluded.amount_to_invest, currency = excluded.currency,
        horizon_years = excluded.horizon_years, risk_profile = excluded.risk_profile,
        exclusions = excluded.exclusions, objectives = excluded.objectives,
        pea_opened_on = excluded.pea_opened_on, updated_at = excluded.updated_at
      """;

  private final JdbcClient jdbc;

  JdbcInvestorProfileRepository(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public Optional<InvestorProfile> find() {
    return jdbc.sql(FIND).query((rs, row) -> map(rs)).optional();
  }

  @Override
  public void save(InvestorProfile profile) {
    jdbc.sql(UPSERT)
        .param("amount", profile.amountToInvest())
        .param("currency", Amounts.EUR.getCurrencyCode())
        .param("horizon", profile.horizonYears())
        .param("risk", profile.riskProfile().name())
        .param("exclusions", profile.exclusions().toArray(String[]::new))
        .param("objectives", profile.objectives(), Types.VARCHAR)
        .param("openedOn", profile.peaOpenedOn(), Types.DATE)
        .param("at", Timestamps.of(profile.updatedAt()))
        .update();
  }

  private static InvestorProfile map(ResultSet rs) throws SQLException {
    return new InvestorProfile(
        rs.getBigDecimal("amount_to_invest"),
        rs.getInt("horizon_years"),
        RiskProfile.valueOf(rs.getString("risk_profile")),
        exclusions(rs.getArray("exclusions")),
        rs.getString("objectives"),
        rs.getObject("pea_opened_on", LocalDate.class),
        rs.getObject("updated_at", OffsetDateTime.class).toInstant());
  }

  private static List<String> exclusions(Array array) throws SQLException {
    try {
      return List.of((String[]) array.getArray());
    } finally {
      array.free();
    }
  }
}
