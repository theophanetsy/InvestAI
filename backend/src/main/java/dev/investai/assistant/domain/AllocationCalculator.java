package dev.investai.assistant.domain;

import dev.investai.market.domain.DailyClose;
import dev.investai.market.domain.InstrumentId;
import dev.investai.market.domain.PeaEligibilityStatus;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Currency;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/**
 * Computes, without recording anything, what a split of the sum to invest would buy (MCP tool
 * {@code computeAllocation}, spec 3.1, criterion 30): the model never computes amounts nor numbers
 * of shares itself. {@link BigDecimal} throughout:
 *
 * <ul>
 *   <li>target = sum × weight / 100, rounded <b>down</b> to the cent: rounded half-even, the
 *       targets could add up to a few cents more than the sum, and the shares bought cost more than
 *       the sum (see the test);
 *   <li>quantity = whole part of target / latest close in euros;
 *   <li>cost = quantity × close, rounded half-even to the cent: at most the target, a whole number
 *       of cents;
 *   <li>remaining = sum − Σ costs, never negative since Σ costs ≤ Σ targets ≤ sum.
 * </ul>
 */
public final class AllocationCalculator {

  private static final Currency EUR = Currency.getInstance("EUR");
  private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
  private static final int CENTS = 2;
  private static final BigDecimal NO_CENT = BigDecimal.ZERO.setScale(CENTS);

  private AllocationCalculator() {}

  /**
   * Computes an allocation.
   *
   * @param amount the sum to invest, in euros at scale 2, positive
   * @param asOf session of the latest computation of the indicators, {@code null} when it never ran
   * @param candidates the proposed lines: at least one, one per instrument, positive weights adding
   *     up to 100 at most
   * @return the allocation
   */
  public static Allocation compute(
      BigDecimal amount, @Nullable LocalDate asOf, List<AllocationCandidate> candidates) {
    check(amount, candidates);
    List<AllocationLine> lines =
        candidates.stream().map(candidate -> line(amount, asOf, candidate)).toList();
    BigDecimal invested = lines.stream().map(AllocationLine::cost).reduce(NO_CENT, BigDecimal::add);
    return new Allocation(amount, asOf, lines, invested, amount.subtract(invested));
  }

  private static void check(BigDecimal amount, List<AllocationCandidate> candidates) {
    if (amount.signum() <= 0 || candidates.isEmpty()) {
      throw new IllegalArgumentException("Nothing to allocate");
    }
    Set<InstrumentId> seen = new HashSet<>();
    boolean valid =
        candidates.stream()
            .allMatch(
                candidate ->
                    candidate.weightPercent().signum() > 0
                        && seen.add(candidate.instrument().id()));
    if (!valid) {
      throw new IllegalArgumentException("Weights must be positive, one line per instrument");
    }
    BigDecimal total =
        candidates.stream()
            .map(AllocationCandidate::weightPercent)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    if (total.compareTo(HUNDRED) > 0) {
      throw new IllegalArgumentException("Weights add up to more than 100 %");
    }
  }

  private static AllocationLine line(
      BigDecimal amount, @Nullable LocalDate asOf, AllocationCandidate candidate) {
    BigDecimal target =
        amount.multiply(candidate.weightPercent()).divide(HUNDRED, CENTS, RoundingMode.DOWN);
    Set<AllocationWarning> warnings =
        eligibilityWarnings(candidate.instrument().peaEligibility().status());
    DailyClose close = candidate.lastClose();
    if (close == null || !EUR.equals(close.currency())) {
      warnings.add(AllocationWarning.NO_EUR_CLOSE);
      return new AllocationLine(
          candidate.instrument(),
          candidate.weightPercent(),
          target,
          null,
          0,
          NO_CENT,
          NO_CENT,
          List.copyOf(warnings));
    }
    if (asOf != null && close.tradingDate().isBefore(asOf)) {
      warnings.add(AllocationWarning.CLOSE_BEFORE_AS_OF);
    }
    long quantity = target.divide(close.close(), 0, RoundingMode.DOWN).longValueExact();
    if (quantity == 0) {
      warnings.add(AllocationWarning.TARGET_BELOW_ONE_SHARE);
    }
    BigDecimal cost =
        close
            .close()
            .multiply(BigDecimal.valueOf(quantity))
            .setScale(CENTS, RoundingMode.HALF_EVEN);
    return new AllocationLine(
        candidate.instrument(),
        candidate.weightPercent(),
        target,
        close,
        quantity,
        cost,
        cost.multiply(HUNDRED).divide(amount, CENTS, RoundingMode.HALF_EVEN),
        List.copyOf(warnings));
  }

  private static Set<AllocationWarning> eligibilityWarnings(PeaEligibilityStatus eligibility) {
    Set<AllocationWarning> warnings = EnumSet.noneOf(AllocationWarning.class);
    if (eligibility == PeaEligibilityStatus.NOT_ELIGIBLE) {
      warnings.add(AllocationWarning.NOT_ELIGIBLE);
    } else if (eligibility == PeaEligibilityStatus.UNKNOWN) {
      warnings.add(AllocationWarning.ELIGIBILITY_UNKNOWN);
    }
    return warnings;
  }
}
