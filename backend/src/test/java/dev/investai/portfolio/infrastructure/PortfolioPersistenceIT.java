package dev.investai.portfolio.infrastructure;

import static dev.investai.market.MarketTestData.isin;
import static dev.investai.market.MarketTestData.listed;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.investai.market.domain.Instrument;
import dev.investai.market.domain.InstrumentRepository;
import dev.investai.portfolio.PortfolioTestData;
import dev.investai.portfolio.domain.Cash;
import dev.investai.portfolio.domain.InvestorProfile;
import dev.investai.portfolio.domain.InvestorProfileRepository;
import dev.investai.portfolio.domain.PortfolioLine;
import dev.investai.portfolio.domain.PortfolioLineId;
import dev.investai.portfolio.domain.PortfolioRepository;
import dev.investai.portfolio.domain.RiskProfile;
import dev.investai.support.IntegrationTest;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;

/** Profile and declared portfolio on the real database (spec 2.3, criteria 1 to 4). */
@IntegrationTest
class PortfolioPersistenceIT {

  private static final Instant NOW = Instant.parse("2026-10-19T08:00:00Z");

  @Autowired private JdbcClient jdbc;
  @Autowired private Flyway flyway;
  @Autowired private InvestorProfileRepository profiles;
  @Autowired private PortfolioRepository portfolio;
  @Autowired private InstrumentRepository instruments;

  @BeforeEach
  void clear() {
    PortfolioTestData.clearTables(jdbc);
  }

  @Test
  void migrationsAreApplied() {
    assertThat(flyway.info().applied())
        .extracting(MigrationInfo::getScript)
        .contains("V4__pea_eligibility.sql", "V5__investor_profile_and_portfolio.sql");
  }

  @Test
  void profileIsASingleRowReplacedOnEverySave() {
    var first =
        new InvestorProfile(
            new BigDecimal("20000.50"),
            10,
            RiskProfile.BALANCED,
            List.of("Tabac", "Armement"),
            "Préparer ma retraite\n<b>sans</b> HTML",
            LocalDate.parse("2019-03-01"),
            NOW);
    var second =
        new InvestorProfile(
            new BigDecimal("15000"), 25, RiskProfile.AGGRESSIVE, List.of(), null, null, NOW);

    assertThat(profiles.find()).isEmpty();
    profiles.save(first);
    assertThat(profiles.find()).hasValue(first);
    profiles.save(second);

    assertThat(profiles.find()).hasValue(second);
    assertThat(jdbc.sql("SELECT count(*) FROM investor_profile").query(Integer.class).single())
        .isEqualTo(1);
  }

  @Test
  void oneLinePerInstrumentUpdatedAndRemovedById() {
    Instrument airLiquide = load("FR000012007", "AI", "Air Liquide");
    var line =
        new PortfolioLine(
            PortfolioLineId.newId(), airLiquide.id(), 10, new BigDecimal("150.1234"), NOW);
    var duplicate = new PortfolioLine(PortfolioLineId.newId(), airLiquide.id(), 1, null, NOW);

    assertThat(portfolio.insert(line)).isTrue();
    assertThat(portfolio.insert(duplicate)).isFalse();
    assertThat(portfolio.findLines()).containsExactly(line);

    var updated = line.update(12, null, NOW.plusSeconds(60));
    assertThat(portfolio.update(updated)).isTrue();
    assertThat(portfolio.findLine(line.id())).hasValue(updated);
    assertThat(portfolio.update(duplicate)).isFalse();

    assertThat(portfolio.delete(line.id())).isTrue();
    assertThat(portfolio.delete(line.id())).isFalse();
    assertThat(portfolio.findLines()).isEmpty();
  }

  @Test
  void cashIsASingleRowReplacedOnEverySave() {
    assertThat(portfolio.findCash()).isEmpty();

    portfolio.saveCash(new Cash(new BigDecimal("350.5"), NOW));
    portfolio.saveCash(new Cash(new BigDecimal("120"), NOW.plusSeconds(1)));

    assertThat(portfolio.findCash())
        .hasValue(new Cash(new BigDecimal("120.00"), NOW.plusSeconds(1)));
  }

  @Test
  void schemaRefusesAnotherCurrencyOrASecondProfile() {
    Instrument airLiquide = load("FR000012007", "AI", "Air Liquide");

    assertThatThrownBy(
            () ->
                jdbc.sql(
                        """
                        INSERT INTO portfolio_line (id, instrument_id, quantity, currency,
                                                    created_at, updated_at)
                        VALUES (gen_random_uuid(), :id, 1, 'USD', now(), now())
                        """)
                    .param("id", airLiquide.id().value())
                    .update())
        .hasMessageContaining("portfolio_line_currency_check");
    assertThatThrownBy(
            () ->
                jdbc.sql(
                        """
                        INSERT INTO investor_profile (singleton, amount_to_invest, currency,
                                                      horizon_years, risk_profile, updated_at)
                        VALUES (false, 1, 'EUR', 1, 'PRUDENT', now())
                        """)
                    .update())
        .hasMessageContaining("investor_profile_singleton_check");
  }

  private Instrument load(String isinBody, String ticker, String name) {
    var entry = listed(isin(isinBody), ticker, name);
    instruments.upsertReference(List.of(entry), NOW);
    return instruments.findByListing(entry.listing()).orElseThrow();
  }
}
