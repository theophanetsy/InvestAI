package dev.investai.market.domain;

import java.util.List;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/**
 * PEA eligibility rule validated by the owner (spec 2.3, criterion 7). An indication only: the
 * broker has the final say, and the owner can correct any instrument ({@link
 * PeaEligibilitySource#OWNER}), which the rule never overrides.
 *
 * <ul>
 *   <li>Shares: eligible when the country of the ISIN is in the European Union or the European
 *       Economic Area, otherwise not eligible. Known limits: the country of the ISIN is not always
 *       the one of the registered office, and the SIIC real-estate companies are excluded from the
 *       PEA without the rule knowing it.
 *   <li>ETFs, funds and other instruments: unknown, eligibility does not follow from the ISIN.
 *   <li>Configured benchmarks (CW8, WPEA, CAC): eligible, whatever their type.
 * </ul>
 *
 * @param benchmarks listings known to be PEA-eligible ({@code investai.market.benchmarks})
 */
public record PeaEligibilityRule(List<Listing> benchmarks) {

  /**
   * ISO 3166 codes of the 27 member states of the European Union, then of Iceland, Norway and
   * Liechtenstein (European Economic Area). Greece is {@code GR} in ISINs.
   */
  public static final Set<String> EU_EEA_COUNTRIES =
      Set.of(
          "AT", "BE", "BG", "HR", "CY", "CZ", "DK", "EE", "FI", "FR", "DE", "GR", "HU", "IE", "IT",
          "LV", "LT", "LU", "MT", "NL", "PL", "PT", "RO", "SK", "SI", "ES", "SE", "IS", "NO", "LI");

  private static final int COUNTRY_LENGTH = 2;

  /** Defensive copy. */
  public PeaEligibilityRule {
    benchmarks = List.copyOf(benchmarks);
  }

  /**
   * Applies the rule.
   *
   * @param listing ISIN and venue
   * @param type kind of instrument
   * @return the eligibility computed by the rule
   */
  public PeaEligibility evaluate(Listing listing, InstrumentType type) {
    if (benchmarks.contains(listing)) {
      return PeaEligibility.byRule(PeaEligibilityStatus.ELIGIBLE);
    }
    if (type != InstrumentType.STOCK) {
      return PeaEligibility.byRule(PeaEligibilityStatus.UNKNOWN);
    }
    String country = listing.isin().value().substring(0, COUNTRY_LENGTH);
    return PeaEligibility.byRule(
        EU_EEA_COUNTRIES.contains(country)
            ? PeaEligibilityStatus.ELIGIBLE
            : PeaEligibilityStatus.NOT_ELIGIBLE);
  }

  /**
   * The eligibility of an instrument: the owner's correction when there is one, the rule otherwise.
   *
   * @param listing ISIN and venue
   * @param type kind of instrument
   * @param correction the owner's correction, {@code null} when none
   * @return the eligibility
   */
  public PeaEligibility resolve(
      Listing listing, InstrumentType type, @Nullable PeaEligibilityStatus correction) {
    return correction == null ? evaluate(listing, type) : PeaEligibility.byOwner(correction);
  }
}
