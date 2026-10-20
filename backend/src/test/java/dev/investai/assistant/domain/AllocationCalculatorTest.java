package dev.investai.assistant.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import dev.investai.market.domain.DailyClose;
import dev.investai.market.domain.Instrument;
import dev.investai.market.domain.InstrumentId;
import dev.investai.market.domain.InstrumentType;
import dev.investai.market.domain.Isin;
import dev.investai.market.domain.Listing;
import dev.investai.market.domain.Mic;
import dev.investai.market.domain.PeaEligibility;
import dev.investai.market.domain.PeaEligibilityStatus;
import dev.investai.market.domain.PriceSource;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Currency;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

/**
 * Amounts and numbers of shares computed by the server (MCP tool computeAllocation, spec 3.1,
 * criterion 30). Every expected value is derived by hand in the comments.
 */
class AllocationCalculatorTest {

  private static final LocalDate AS_OF = LocalDate.parse("2026-10-16");
  private static final Currency EUR = Currency.getInstance("EUR");
  private static final String THOUSAND_TEXT = "1000.00";
  private static final BigDecimal THOUSAND = new BigDecimal(THOUSAND_TEXT);
  private static final String PRICE_TEXT = "128.79";
  private static final BigDecimal PRICE = new BigDecimal(PRICE_TEXT);
  private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2);
  private static final PeaEligibility ELIGIBLE =
      PeaEligibility.byRule(PeaEligibilityStatus.ELIGIBLE);

  @Test
  void amountsSharesCostsAndWeightsDerivedByHand() {
    // Sum 20,000.00 €, 10 % left unattributed.
    // A: 40 % -> target 8,000.00; 8,000 / 152.36 = 52.5 -> 52 shares (53 x 152.36 = 8,075.08);
    //    cost 52 x 152.36 = 7,922.72; actual weight 7,922.72 / 20,000 = 39.6136 % -> 39.61.
    // B: 30 % -> target 6,000.00; 6,000 / 98.5 = 60.9 -> 60 shares; cost 5,910.00; 29.55 %.
    // C: 20 % -> target 4,000.00 < one share at 7,000: 0 share, cost 0.00, 0.00 %, warned.
    // Invested 7,922.72 + 5,910.00 = 13,832.72; remaining 20,000.00 - 13,832.72 = 6,167.28.
    Allocation allocation =
        AllocationCalculator.compute(
            new BigDecimal("20000.00"),
            AS_OF,
            List.of(
                candidate("40", ELIGIBLE, eur("152.36", AS_OF)),
                candidate("30", ELIGIBLE, eur("98.5", AS_OF)),
                candidate("20", ELIGIBLE, eur("7000", AS_OF))));

    assertThat(allocation.lines())
        .extracting(
            AllocationLine::targetAmount,
            AllocationLine::quantity,
            AllocationLine::cost,
            AllocationLine::actualWeightPercent)
        .containsExactly(
            tuple("8000.00", 52, "7922.72", "39.61"),
            tuple("6000.00", 60, "5910.00", "29.55"),
            tuple("4000.00", 0, "0.00", "0.00"));
    assertThat(allocation.lines().get(2).warnings())
        .containsExactly(AllocationWarning.TARGET_BELOW_ONE_SHARE);
    assertThat(allocation.investedTotal()).isEqualByComparingTo("13832.72");
    assertThat(allocation.investedTotal().scale()).isEqualTo(2);
    assertThat(allocation.remainingCash()).isEqualTo(new BigDecimal("6167.28"));
  }

  @Test
  void targetIsRoundedDownSoThatTheRemainingCashIsNeverNegative() {
    // Sum 1,030.30 €, four lines of 25 %: exact target 257.575 each. Rounded half-even it would
    // be 257.58, i.e. two shares at 128.79 = 257.58 each and 1,030.32 invested: 0.02 € more than
    // the sum. Rounded down, the target is 257.57: one share (2 x 128.79 = 257.58 > 257.57),
    // cost 128.79; invested 4 x 128.79 = 515.16, remaining 1,030.30 - 515.16 = 515.14.
    Allocation allocation =
        AllocationCalculator.compute(
            new BigDecimal("1030.30"),
            AS_OF,
            List.of(
                candidate("25", ELIGIBLE, eur(PRICE_TEXT, AS_OF)),
                candidate("25", ELIGIBLE, eur(PRICE_TEXT, AS_OF)),
                candidate("25", ELIGIBLE, eur(PRICE_TEXT, AS_OF)),
                candidate("25", ELIGIBLE, eur(PRICE_TEXT, AS_OF))));

    assertThat(allocation.lines())
        .allSatisfy(
            line -> {
              assertThat(line.targetAmount()).isEqualTo(new BigDecimal("257.57"));
              assertThat(line.quantity()).isEqualTo(1);
              assertThat(line.cost()).isEqualTo(PRICE);
            });
    assertThat(allocation.investedTotal()).isEqualTo(new BigDecimal("515.16"));
    assertThat(allocation.remainingCash()).isEqualTo(new BigDecimal("515.14"));
  }

  @Test
  void costIsRoundedHalfEvenToTheCentAndNeverAboveTheTarget() {
    // Sum 1,000.00 €, 10 % -> target 100.00; 100 / 3.3333 = 30.0003 -> 30 shares;
    // 30 x 3.3333 = 99.999 -> 100.00 (half-even); weight 10.00 %; remaining 900.00.
    Allocation allocation =
        AllocationCalculator.compute(
            THOUSAND, AS_OF, List.of(candidate("10", ELIGIBLE, eur("3.3333", AS_OF))));

    AllocationLine line = allocation.lines().getFirst();
    assertThat(line.quantity()).isEqualTo(30);
    assertThat(line.cost()).isEqualTo(new BigDecimal("100.00"));
    assertThat(line.actualWeightPercent()).isEqualTo(new BigDecimal("10.00"));
    assertThat(allocation.remainingCash()).isEqualTo(new BigDecimal("900.00"));
  }

  @Test
  void fullyAttributedAndExactlyInvested() {
    // Sum 1,000.00 €: 50 % at 100 -> 5 shares, 500.00; 50 % at 250 -> 2 shares, 500.00.
    // Invested 1,000.00, remaining 0.00, weights 50.00 % each.
    Allocation allocation =
        AllocationCalculator.compute(
            THOUSAND,
            AS_OF,
            List.of(
                candidate("50", ELIGIBLE, eur("100", AS_OF)),
                candidate("50.00", ELIGIBLE, eur("250", AS_OF))));

    assertThat(allocation.lines())
        .extracting(AllocationLine::quantity, AllocationLine::actualWeightPercent)
        .containsExactly(
            org.assertj.core.groups.Tuple.tuple(5L, new BigDecimal("50.00")),
            org.assertj.core.groups.Tuple.tuple(2L, new BigDecimal("50.00")));
    assertThat(allocation.remainingCash()).isEqualTo(ZERO);
  }

  @Test
  void aLineWithoutCloseInEurosIsNeverBought() {
    // A close in dollars is not converted, a missing close is not guessed: 0 share, 0.00 cost,
    // no close given back; the target stays (50 % of 1,000.00 = 500.00 each).
    DailyClose dollars =
        new DailyClose(
            InstrumentId.newId(),
            AS_OF,
            BigDecimal.TEN,
            Currency.getInstance("USD"),
            PriceSource.EURONEXT);
    Allocation allocation =
        AllocationCalculator.compute(
            THOUSAND,
            AS_OF,
            List.of(candidate("50", ELIGIBLE, dollars), candidate("50", ELIGIBLE, null)));

    assertThat(allocation.lines())
        .allSatisfy(
            line -> {
              assertThat(line.targetAmount()).isEqualTo(new BigDecimal("500.00"));
              assertThat(line.quantity()).isZero();
              assertThat(line.cost()).isEqualTo(ZERO);
              assertThat(line.lastClose()).isNull();
              assertThat(line.warnings()).containsExactly(AllocationWarning.NO_EUR_CLOSE);
            });
    assertThat(allocation.remainingCash()).isEqualTo(THOUSAND);
  }

  @Test
  void eligibilityAndStaleCloseAreWarnedInTheOrderOfTheEnum() {
    // Close of Thursday, indicators of Friday: the close is older than asOf.
    LocalDate thursday = AS_OF.minusDays(1);
    Allocation allocation =
        AllocationCalculator.compute(
            THOUSAND,
            AS_OF,
            List.of(
                candidate(
                    "10",
                    PeaEligibility.byOwner(PeaEligibilityStatus.NOT_ELIGIBLE),
                    eur("10", thursday)),
                candidate(
                    "10", PeaEligibility.byRule(PeaEligibilityStatus.UNKNOWN), eur("200", AS_OF))));

    assertThat(allocation.lines().get(0).warnings())
        .containsExactly(AllocationWarning.NOT_ELIGIBLE, AllocationWarning.CLOSE_BEFORE_AS_OF);
    assertThat(allocation.lines().get(1).warnings())
        .containsExactly(
            AllocationWarning.ELIGIBILITY_UNKNOWN, AllocationWarning.TARGET_BELOW_ONE_SHARE);
  }

  @Test
  void withoutComputationNoCloseIsDeemedStale() {
    Allocation allocation =
        AllocationCalculator.compute(
            THOUSAND,
            null,
            List.of(candidate("10", ELIGIBLE, eur("10", LocalDate.parse("2020-01-02")))));

    assertThat(allocation.asOf()).isNull();
    assertThat(allocation.lines().getFirst().warnings()).isEmpty();
  }

  @Test
  void weightsArePositiveAndAddUpToAtMostOneHundredOncePerInstrument() {
    var amount = THOUSAND;
    var same = candidate("10", ELIGIBLE, null);

    assertThatIllegalArgumentException()
        .isThrownBy(
            () ->
                AllocationCalculator.compute(
                    amount,
                    AS_OF,
                    List.of(candidate("60", ELIGIBLE, null), candidate("40.01", ELIGIBLE, null))));
    assertThatIllegalArgumentException()
        .isThrownBy(
            () ->
                AllocationCalculator.compute(
                    amount, AS_OF, List.of(candidate("0.00", ELIGIBLE, null))));
    assertThatIllegalArgumentException()
        .isThrownBy(() -> AllocationCalculator.compute(amount, AS_OF, List.of(same, same)));
    assertThatIllegalArgumentException()
        .isThrownBy(() -> AllocationCalculator.compute(amount, AS_OF, List.of()));
  }

  private static AllocationCandidate candidate(
      String weight, PeaEligibility eligibility, @Nullable DailyClose close) {
    InstrumentId id = close == null ? InstrumentId.newId() : close.instrumentId();
    var instrument =
        new Instrument(
            id,
            new Listing(new Isin("FR0000120073"), Mic.XPAR),
            "AI",
            "Air Liquide",
            InstrumentType.STOCK,
            EUR,
            null,
            false,
            eligibility);
    return new AllocationCandidate(instrument, new BigDecimal(weight), close);
  }

  private static DailyClose eur(String price, LocalDate date) {
    return new DailyClose(
        InstrumentId.newId(), date, new BigDecimal(price), EUR, PriceSource.EURONEXT);
  }

  private static org.assertj.core.groups.Tuple tuple(
      String target, long quantity, String cost, String weight) {
    return org.assertj.core.groups.Tuple.tuple(
        new BigDecimal(target), quantity, new BigDecimal(cost), new BigDecimal(weight));
  }
}
