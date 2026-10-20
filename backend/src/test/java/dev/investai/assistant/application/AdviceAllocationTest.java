package dev.investai.assistant.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.investai.assistant.application.AdviceAllocation.ProposedLine;
import dev.investai.assistant.domain.Allocation;
import dev.investai.market.IndicatorFixtures;
import dev.investai.market.application.MarketFreshness;
import dev.investai.market.application.MarketFreshness.Freshness;
import dev.investai.market.application.MarketQueries;
import dev.investai.market.application.MarketQueries.InstrumentDetails;
import dev.investai.market.domain.IndicatorRun;
import dev.investai.market.domain.Instrument;
import dev.investai.market.domain.InstrumentId;
import dev.investai.portfolio.application.InvestorProfiles;
import dev.investai.portfolio.domain.InvestorProfile;
import dev.investai.portfolio.domain.RiskProfile;
import dev.investai.shared.domain.ResourceNotFoundException;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class AdviceAllocationTest {

  private static final LocalDate FRIDAY = LocalDate.parse("2026-10-16");
  private static final Instant NOW = Instant.parse("2026-10-19T08:00:00Z");

  private final InvestorProfiles profiles = mock(InvestorProfiles.class);
  private final MarketQueries market = mock(MarketQueries.class);
  private final MarketFreshness freshness = mock(MarketFreshness.class);
  private final AdviceAllocation allocation = new AdviceAllocation(profiles, market, freshness);
  private final Instrument airLiquide = IndicatorFixtures.airLiquide();
  private final List<ProposedLine> lines =
      List.of(new ProposedLine(airLiquide.id(), new BigDecimal("50")));

  @Test
  void computesOnTheSumOfTheProfileAndTheSessionOfTheIndicators() {
    when(profiles.find()).thenReturn(Optional.of(profile()));
    var close = IndicatorFixtures.close(airLiquide.id(), FRIDAY, "170");
    when(market.details(anyCollection()))
        .thenReturn(Map.of(airLiquide.id(), new InstrumentDetails(airLiquide, close)));
    when(freshness.current()).thenReturn(new Freshness(FRIDAY, new IndicatorRun(FRIDAY, NOW)));

    Allocation computed = allocation.compute(lines).orElseThrow();

    // 50 % of 1,000.00 = 500.00; 500 / 170 = 2.9 -> 2 shares, 340.00.
    assertThat(computed.asOf()).isEqualTo(FRIDAY);
    assertThat(computed.lines().getFirst().quantity()).isEqualTo(2);
    assertThat(computed.remainingCash()).isEqualTo(new BigDecimal("660.00"));
  }

  @Test
  void nothingWithoutProfileNorAnyLookup() {
    when(profiles.find()).thenReturn(Optional.empty());

    assertThat(allocation.compute(lines)).isEmpty();
    Mockito.verifyNoInteractions(market, freshness);
  }

  @Test
  void unknownInstrumentIsNotFound() {
    when(profiles.find()).thenReturn(Optional.of(profile()));
    when(market.details(anyCollection())).thenReturn(Map.of());

    assertThatThrownBy(
            () ->
                allocation.compute(List.of(new ProposedLine(InstrumentId.newId(), BigDecimal.ONE))))
        .isInstanceOfSatisfying(
            ResourceNotFoundException.class,
            e -> assertThat(e.code()).isEqualTo(AdviceAllocation.INSTRUMENT_NOT_FOUND));
  }

  private static InvestorProfile profile() {
    return new InvestorProfile(
        new BigDecimal("1000.00"), 10, RiskProfile.BALANCED, List.of(), null, null, NOW);
  }
}
