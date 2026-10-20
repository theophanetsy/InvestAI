package dev.investai.assistant.application;

import dev.investai.assistant.domain.Allocation;
import dev.investai.assistant.domain.AllocationCalculator;
import dev.investai.assistant.domain.AllocationCandidate;
import dev.investai.market.application.MarketFreshness;
import dev.investai.market.application.MarketQueries;
import dev.investai.market.application.MarketQueries.InstrumentDetails;
import dev.investai.market.domain.IndicatorRun;
import dev.investai.market.domain.InstrumentId;
import dev.investai.portfolio.application.InvestorProfiles;
import dev.investai.portfolio.domain.InvestorProfile;
import dev.investai.shared.domain.ResourceNotFoundException;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Service;

/**
 * Computes a proposed split of the sum of the profile (MCP tool {@code computeAllocation}, spec
 * 3.1, criteria 28 to 31) on the latest stored closes. Records nothing.
 */
@Service
public class AdviceAllocation {

  /** Error code of a proposed instrument absent from the reference data. */
  public static final String INSTRUMENT_NOT_FOUND = "instrument.not-found";

  private final InvestorProfiles profiles;
  private final MarketQueries market;
  private final MarketFreshness freshness;

  /**
   * Creates the use case.
   *
   * @param profiles the investor profile (the sum to invest)
   * @param market instruments and latest closes
   * @param freshness latest computation of the indicators
   */
  public AdviceAllocation(
      InvestorProfiles profiles, MarketQueries market, MarketFreshness freshness) {
    this.profiles = profiles;
    this.market = market;
    this.freshness = freshness;
  }

  /**
   * Computes what the proposed lines would buy with the sum of the profile.
   *
   * @param lines the proposed lines: weights in percent of the sum, one line per instrument
   * @return the allocation, empty while the profile is not filled in
   * @throws ResourceNotFoundException {@value #INSTRUMENT_NOT_FOUND} when an instrument is unknown
   * @throws IllegalArgumentException when the weights are not positive, add up to more than 100 or
   *     name an instrument twice
   */
  public Optional<Allocation> compute(List<ProposedLine> lines) {
    Optional<InvestorProfile> profile = profiles.find();
    if (profile.isEmpty()) {
      return Optional.empty();
    }
    Map<InstrumentId, InstrumentDetails> details =
        market.details(lines.stream().map(ProposedLine::instrumentId).toList());
    List<AllocationCandidate> candidates =
        lines.stream().map(line -> candidate(line, details)).toList();
    IndicatorRun run = freshness.current().lastRun();
    return Optional.of(
        AllocationCalculator.compute(
            profile.get().amountToInvest(), run == null ? null : run.asOf(), candidates));
  }

  private static AllocationCandidate candidate(
      ProposedLine line, Map<InstrumentId, InstrumentDetails> details) {
    InstrumentDetails found = details.get(line.instrumentId());
    if (found == null) {
      // No identifier in the message: it is the model's input.
      throw new ResourceNotFoundException(INSTRUMENT_NOT_FOUND, "Unknown instrument in a split");
    }
    return new AllocationCandidate(found.instrument(), line.weightPercent(), found.lastClose());
  }

  /**
   * A line proposed by the model.
   *
   * @param instrumentId the instrument
   * @param weightPercent share of the sum, in percent
   */
  public record ProposedLine(InstrumentId instrumentId, BigDecimal weightPercent) {}
}
