package dev.investai.assistant.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.investai.assistant.domain.HealthStatus;
import dev.investai.assistant.domain.IndicatorsFreshness;
import dev.investai.market.application.MarketFreshness;
import dev.investai.market.application.MarketFreshness.Freshness;
import dev.investai.market.domain.IndicatorRun;
import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class AssistantHealthTest {

  private static final String VERSION = "1.2.3";

  private final MarketFreshness market = mock(MarketFreshness.class);
  private final AssistantHealth health = new AssistantHealth(market, VERSION);

  @Test
  void reportsTheVersionAndTheDatesOfTheMarketData() {
    var friday = LocalDate.parse("2026-10-16");
    var computedAt = Instant.parse("2026-10-19T08:00:00Z");
    when(market.current()).thenReturn(new Freshness(friday, new IndicatorRun(friday, computedAt)));

    var report = health.report();

    assertThat(report.version()).isEqualTo(VERSION);
    assertThat(report.closesAsOf()).isEqualTo(friday);
    assertThat(report.indicators()).isEqualTo(new IndicatorsFreshness(friday, computedAt));
    assertThat(report.status()).isEqualTo(HealthStatus.READY);
  }

  @Test
  void reportsAnEmptyDatabase() {
    when(market.current()).thenReturn(new Freshness(null, null));

    var report = health.report();

    assertThat(report.closesAsOf()).isNull();
    assertThat(report.indicators()).isNull();
    assertThat(report.status()).isEqualTo(HealthStatus.NO_CLOSES);
  }
}
