package dev.investai.market.domain;

import static dev.investai.market.MarketTestData.isin;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

/** PEA eligibility rule (spec 2.3, criterion 7). */
class PeaEligibilityRuleTest {

  private static final Listing BENCHMARK = new Listing(isin("LU168104359"), Mic.XPAR);
  private final PeaEligibilityRule rule = new PeaEligibilityRule(List.of(BENCHMARK));

  @Test
  void euAndEeaCountriesAreTheTwentySevenMemberStatesPlusIcelandNorwayAndLiechtenstein() {
    assertThat(PeaEligibilityRule.EU_EEA_COUNTRIES)
        .containsExactlyInAnyOrder(
            // European Union (27): Greece is GR in ISO 3166 and ISIN, not EL.
            "AT",
            "BE",
            "BG",
            "HR",
            "CY",
            "CZ",
            "DK",
            "EE",
            "FI",
            "FR",
            "DE",
            "GR",
            "HU",
            "IE",
            "IT",
            "LV",
            "LT",
            "LU",
            "MT",
            "NL",
            "PL",
            "PT",
            "RO",
            "SK",
            "SI",
            "ES",
            "SE",
            // European Economic Area
            "IS",
            "NO",
            "LI");
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "AT", "BE", "BG", "HR", "CY", "CZ", "DK", "EE", "FI", "FR", "DE", "GR", "HU", "IE", "IT",
        "LV", "LT", "LU", "MT", "NL", "PL", "PT", "RO", "SK", "SI", "ES", "SE", "IS", "NO", "LI"
      })
  void stockFromTheEuOrEeaIsProbablyEligible(String country) {
    assertThat(rule.evaluate(stock(country), InstrumentType.STOCK))
        .isEqualTo(PeaEligibility.byRule(PeaEligibilityStatus.ELIGIBLE));
  }

  @ParameterizedTest
  @ValueSource(strings = {"GB", "US", "CH", "MC", "JE", "GG", "XS", "CA", "BM"})
  void stockFromElsewhereIsNotEligible(String country) {
    assertThat(rule.evaluate(stock(country), InstrumentType.STOCK))
        .isEqualTo(PeaEligibility.byRule(PeaEligibilityStatus.NOT_ELIGIBLE));
  }

  @ParameterizedTest
  @EnumSource(
      value = InstrumentType.class,
      names = {"ETF", "FUND", "OTHER"})
  void fundsAndOtherInstrumentsAreUnknownEvenFromFrance(InstrumentType type) {
    assertThat(rule.evaluate(stock("FR"), type))
        .isEqualTo(PeaEligibility.byRule(PeaEligibilityStatus.UNKNOWN));
  }

  @ParameterizedTest
  @EnumSource(InstrumentType.class)
  void configuredBenchmarksAreEligibleWhateverTheirType(InstrumentType type) {
    assertThat(rule.evaluate(BENCHMARK, type))
        .isEqualTo(PeaEligibility.byRule(PeaEligibilityStatus.ELIGIBLE));
  }

  @Test
  void benchmarkOnAnotherVenueIsNotABenchmark() {
    Listing elsewhere = new Listing(BENCHMARK.isin(), new Mic("ETFP"));

    assertThat(rule.evaluate(elsewhere, InstrumentType.ETF).status())
        .isEqualTo(PeaEligibilityStatus.UNKNOWN);
  }

  @ParameterizedTest
  @EnumSource(PeaEligibilityStatus.class)
  void ownerCorrectionAlwaysWinsOverTheRule(PeaEligibilityStatus corrected) {
    assertThat(rule.resolve(stock("US"), InstrumentType.STOCK, corrected))
        .isEqualTo(new PeaEligibility(corrected, PeaEligibilitySource.OWNER));
    assertThat(rule.resolve(BENCHMARK, InstrumentType.ETF, corrected))
        .isEqualTo(new PeaEligibility(corrected, PeaEligibilitySource.OWNER));
  }

  @Test
  void withoutCorrectionTheRuleApplies() {
    assertThat(rule.resolve(stock("FR"), InstrumentType.STOCK, null))
        .isEqualTo(PeaEligibility.byRule(PeaEligibilityStatus.ELIGIBLE));
  }

  @Test
  void benchmarksAreCopied() {
    var benchmarks = new java.util.ArrayList<>(List.of(BENCHMARK));
    var copy = new PeaEligibilityRule(benchmarks);
    benchmarks.clear();

    assertThat(copy.evaluate(BENCHMARK, InstrumentType.ETF).status())
        .isEqualTo(PeaEligibilityStatus.ELIGIBLE);
    assertThat(Set.copyOf(copy.benchmarks())).containsExactly(BENCHMARK);
  }

  private static Listing stock(String country) {
    return new Listing(isin(country + "000012007"), Mic.XPAR);
  }
}
