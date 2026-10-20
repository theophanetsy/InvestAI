package dev.investai.assistant.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import dev.investai.assistant.domain.Allocation;
import dev.investai.assistant.domain.AllocationLine;
import dev.investai.assistant.domain.AllocationWarning;
import dev.investai.assistant.domain.DataLimit;
import dev.investai.assistant.domain.ProfileSummary;
import dev.investai.assistant.infrastructure.PortfolioResult.RiskInclusion;
import dev.investai.market.IndicatorFixtures;
import dev.investai.market.domain.Indicator;
import dev.investai.market.domain.Instrument;
import dev.investai.market.domain.InstrumentId;
import dev.investai.portfolio.application.PortfolioIndicators;
import dev.investai.portfolio.application.PortfolioOverview;
import dev.investai.portfolio.domain.Composition;
import dev.investai.portfolio.domain.CorrelationMatrix;
import dev.investai.portfolio.domain.InvestorProfile;
import dev.investai.portfolio.domain.LineExclusion;
import dev.investai.portfolio.domain.PortfolioLine;
import dev.investai.portfolio.domain.PortfolioLineId;
import dev.investai.portfolio.domain.PortfolioRisk;
import dev.investai.portfolio.domain.PricedLine;
import dev.investai.portfolio.domain.Quote;
import dev.investai.portfolio.domain.RiskProfile;
import dev.investai.portfolio.domain.Valuation;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

/**
 * Projections of the tools about the owner, without Spring (spec 3.1, criterion 40): profile,
 * declared PEA and computed split.
 */
class OwnerResultsTest {

  private static final LocalDate FRIDAY = LocalDate.parse("2026-10-16");
  private static final String EUR = "EUR";
  private static final Instant NOW = Instant.parse("2026-10-19T08:00:00Z");

  @Test
  void profileWithoutObjectivesNorExclusions() {
    var profile =
        new InvestorProfile(
            new BigDecimal("20000"), 10, RiskProfile.PRUDENT, List.of(), " ", null, NOW);

    var result = ProfileResult.of(new ProfileSummary(profile, null));

    assertThat(result.status()).isEqualTo(AdviceStatus.OK);
    assertThat(result.amountToInvest()).isEqualTo(new Money("20000.00", EUR));
    assertThat(result.peaFiveYearsReached()).isNull();
    assertThat(result.riskProfile())
        .isEqualTo(
            new ProfileResult.RiskProfileView(
                RiskProfile.PRUDENT, "Prudent", RiskProfile.PRUDENT.description()));
    assertThat(result.untrusted()).isEqualTo(new ProfileResult.UntrustedProfile(null, List.of()));
    assertThat(ProfileResult.missing().status()).isEqualTo(AdviceStatus.PROFILE_MISSING);
  }

  @Test
  void portfolioLinesByDecreasingValueWithoutCloseLastAndTruncated() {
    // 32 lines: line i holds i + 1 shares at 10 €, except line 0 which has no close.
    List<Instrument> held = IntStream.range(0, 32).mapToObj(OwnerResultsTest::instrument).toList();
    List<PricedLine> lines =
        IntStream.range(0, 32)
            .mapToObj(
                i ->
                    new PricedLine(
                        new PortfolioLine(
                            PortfolioLineId.newId(), held.get(i).id(), i + 1L, null, NOW),
                        i == 0 ? null : new Quote(BigDecimal.TEN, FRIDAY)))
            .toList();
    var valuation = Valuation.of(lines, null);
    var risk =
        new PortfolioRisk(
            Indicator.of(new BigDecimal("12.34")),
            Indicator.INSUFFICIENT_HISTORY,
            List.of(),
            new CorrelationMatrix(List.of(), List.of()),
            Map.of(lines.getFirst().line().id(), LineExclusion.NOT_VALUED));
    var portfolio =
        new PortfolioIndicators(
            new PortfolioOverview(
                valuation,
                held.stream().collect(Collectors.toMap(Instrument::id, Function.identity()))),
            FRIDAY,
            Map.of(),
            Composition.of(valuation, Map.of()),
            risk);

    var result = PortfolioResult.of(portfolio, NOW);

    assertThat(result.lineCount()).isEqualTo(32);
    assertThat(result.linesTruncated()).isTrue();
    assertThat(result.lines()).hasSize(PortfolioResult.MAX_LINES);
    assertThat(result.lines().getFirst().quantity()).isEqualTo(32);
    assertThat(result.lines().getFirst().value()).isEqualTo(new Money("320.00", EUR));
    assertThat(result.lines().getFirst().riskInclusion()).isEqualTo(RiskInclusion.INCLUDED);
    assertThat(result.lines().getFirst().indicators()).isNull();
    assertThat(result.valuation().cash()).isNull();
    assertThat(result.risk().volatilityPercent().value()).isEqualTo("12.34");
    assertThat(result.dataLimits())
        .extracting(DataLimitView::code)
        .containsExactly(
            DataLimit.RAW_CLOSES,
            DataLimit.EXCLUDES_DIVIDENDS,
            DataLimit.SHORT_HISTORY,
            DataLimit.LOW_LIQUIDITY,
            DataLimit.PEA_ELIGIBILITY_INDICATIVE);
    assertThat(RiskInclusion.of(LineExclusion.INSUFFICIENT_HISTORY))
        .isEqualTo(RiskInclusion.INSUFFICIENT_HISTORY);
    assertThat(RiskInclusion.of(LineExclusion.NOT_VALUED)).isEqualTo(RiskInclusion.NOT_VALUED);
  }

  @Test
  void allocationGivesAmountsAsDecimalStringsInEuros() {
    var instrument = IndicatorFixtures.airLiquide();
    var close = IndicatorFixtures.close(instrument.id(), FRIDAY, "170");
    var line =
        new AllocationLine(
            instrument,
            new BigDecimal("50.5"),
            new BigDecimal("505.00"),
            close,
            2,
            new BigDecimal("340.00"),
            new BigDecimal("34.00"),
            List.of(AllocationWarning.ELIGIBILITY_UNKNOWN));
    var allocation =
        new Allocation(
            new BigDecimal("1000.00"),
            null,
            List.of(line),
            new BigDecimal("340.00"),
            new BigDecimal("660.00"));

    var result = AllocationResult.of(allocation, NOW);

    assertThat(result.status()).isEqualTo(AdviceStatus.OK);
    assertThat(result.asOf()).isNull();
    assertThat(result.computedAt()).isEqualTo(NOW);
    var computed = result.lines().getFirst();
    assertThat(computed.targetWeightPercent()).isEqualTo("50.5");
    assertThat(computed.targetAmount()).isEqualTo(new Money("505.00", EUR));
    assertThat(computed.lastClose()).isEqualTo(new LastClose("170.0000", FRIDAY));
    assertThat(computed.actualWeightPercent()).isEqualTo("34.00");
    assertThat(computed.warnings()).containsExactly(AllocationWarning.ELIGIBILITY_UNKNOWN);
    assertThat(result.remainingCash()).isEqualTo(new Money("660.00", EUR));
    assertThat(AllocationResult.missing().lines()).isEmpty();
  }

  private static Instrument instrument(int index) {
    var model = IndicatorFixtures.airLiquide();
    return new Instrument(
        InstrumentId.newId(),
        model.listing(),
        model.ticker(),
        "Ligne " + index,
        model.type(),
        model.currency(),
        null,
        false,
        model.peaEligibility());
  }
}
