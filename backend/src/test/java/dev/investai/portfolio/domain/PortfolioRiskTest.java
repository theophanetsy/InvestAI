package dev.investai.portfolio.domain;

import static org.assertj.core.api.Assertions.assertThat;

import dev.investai.market.IndicatorFixtures;
import dev.investai.market.domain.ClosingPrices;
import dev.investai.market.domain.EuronextCalendar;
import dev.investai.market.domain.Indicator;
import dev.investai.market.domain.InstrumentId;
import dev.investai.market.domain.YearOfCloses;
import dev.investai.market.domain.YearWindow;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.IntFunction;
import java.util.stream.IntStream;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

/**
 * Risk of the current portfolio as if held for a year (spec 2.4, criteria 7 and 8), on synthetic
 * series of the 253 sessions ending on Friday 16 October 2026, computed by hand.
 */
class PortfolioRiskTest {

  private static final Instant NOW = Instant.parse("2026-10-19T08:00:00Z");
  private static final LocalDate FRIDAY = LocalDate.parse("2026-10-16");
  private static final YearWindow WINDOW = YearWindow.ending(FRIDAY, new EuronextCalendar());

  private final InstrumentId alternating = InstrumentId.newId();
  private final InstrumentId flat = InstrumentId.newId();
  private final InstrumentId recent = InstrumentId.newId();
  private final InstrumentId unpriced = InstrumentId.newId();
  private final InstrumentId msciWorld = InstrumentId.newId();
  private final InstrumentId cac40 = InstrumentId.newId();
  private final Map<InstrumentId, ClosingPrices> closes = new HashMap<>();

  private ClosingPrices series(InstrumentId id, IntFunction<String> price, Set<Integer> missing) {
    var prices =
        ClosingPrices.of(
            WINDOW.sessions(),
            IntStream.range(0, YearWindow.SESSIONS)
                .filter(i -> !missing.contains(i))
                .mapToObj(
                    i -> IndicatorFixtures.close(id, WINDOW.sessions().get(i), price.apply(i)))
                .toList());
    closes.put(id, prices);
    return prices;
  }

  private YearOfCloses year(ClosingPrices msci, ClosingPrices cac) {
    return new YearOfCloses(WINDOW, closes, msci, cac);
  }

  private static ValuedLine line(InstrumentId instrument, long quantity, @Nullable String close) {
    var priced =
        new PricedLine(
            new PortfolioLine(PortfolioLineId.newId(), instrument, quantity, null, NOW),
            close == null ? null : new Quote(new BigDecimal(close), FRIDAY));
    return Valuation.of(List.of(priced), null).lines().getFirst();
  }

  @Test
  void currentQuantitiesAreReplayedOverTheYear() {
    // 1 × (100/110) + 2 × 50 = 200/210 alternately: returns +5 % and -1/21, each at
    // ±(0.05 + 1/21)/2 = ±0.0488095 from the mean: s = 0.0488095 × √(252/251) = 0.0489067;
    // × √252 = 0.776369 → 77.64 %. Largest fall 210 → 200: -10/210 = -4.76 %.
    series(alternating, i -> i % 2 == 0 ? "100" : "110", Set.of());
    series(flat, i -> "50", Set.of());
    var msci = series(msciWorld, i -> i % 2 == 0 ? "100" : "105", Set.of());
    var cac = series(cac40, i -> "100", Set.of());
    var lines = List.of(line(alternating, 1, "100"), line(flat, 2, "50"));

    PortfolioRisk risk = PortfolioRisk.of(lines, year(msci, cac));

    assertThat(risk.volatility()).isEqualTo(of("77.64"));
    assertThat(risk.maxDrawdown()).isEqualTo(of("-4.76"));
    assertThat(risk.exclusions()).isEmpty();
    assertThat(risk.curve()).hasSize(YearWindow.SESSIONS);
    assertThat(risk.curve().getFirst())
        .isEqualTo(new CurvePoint(WINDOW.start(), hundred(), hundred(), hundred()));
    assertThat(risk.curve().get(1))
        .as("210/200, 105/100, 100/100")
        .isEqualTo(
            new CurvePoint(
                WINDOW.sessions().get(1),
                new BigDecimal("105.00"),
                new BigDecimal("105.00"),
                hundred()));
  }

  @Test
  void correlationsBetweenKeptLines() {
    series(alternating, i -> i % 2 == 0 ? "100" : "110", Set.of());
    series(recent, i -> i % 2 == 0 ? "20" : "21", Set.of());
    series(flat, i -> "50", Set.of());
    var msci = series(msciWorld, i -> "100", Set.of());
    var lines = List.of(line(alternating, 1, "100"), line(recent, 1, "20"), line(flat, 1, "50"));

    CorrelationMatrix matrix = PortfolioRisk.of(lines, year(msci, msci)).correlations();

    assertThat(matrix.instruments()).containsExactly(alternating, recent, flat);
    assertThat(matrix.rows().get(0)).containsExactly(of("1.00"), of("1.00"), notComputable());
    assertThat(matrix.rows().get(1).get(0)).as("same phase").isEqualTo(of("1.00"));
    assertThat(matrix.rows().get(2))
        .as("a price that never moves has no correlation")
        .containsOnly(notComputable());
  }

  @Test
  void linesWithoutPriceOrHistoryAreLeftOutAndReported() {
    series(alternating, i -> i % 2 == 0 ? "100" : "110", Set.of());
    series(recent, i -> "20", rangeTo(YearWindow.SESSIONS - 10));
    var msci = series(msciWorld, i -> "100", Set.of());
    ValuedLine young = line(recent, 1, "20");
    ValuedLine noPrice = line(unpriced, 1, null);
    var lines = List.of(line(alternating, 1, "100"), young, noPrice);

    PortfolioRisk risk = PortfolioRisk.of(lines, year(msci, msci));

    assertThat(risk.exclusions())
        .containsExactlyInAnyOrderEntriesOf(
            Map.of(
                young.line().id(), LineExclusion.INSUFFICIENT_HISTORY,
                noPrice.line().id(), LineExclusion.NOT_VALUED));
    assertThat(risk.volatility()).as("on the alternating line alone").isEqualTo(of("151.83"));
    assertThat(risk.correlations().instruments()).containsExactly(alternating);
  }

  @Test
  void aMissingCloseOfAKeptLineIsAGapInTheCurveNeverInterpolated() {
    series(alternating, i -> i % 2 == 0 ? "100" : "110", Set.of(100));
    var msci = series(msciWorld, i -> "100", Set.of());
    var insufficientCac = series(cac40, i -> "100", rangeTo(100));

    PortfolioRisk risk =
        PortfolioRisk.of(List.of(line(alternating, 1, "100")), year(msci, insufficientCac));

    CurvePoint gap = risk.curve().get(100);
    assertThat(gap.date()).isEqualTo(WINDOW.sessions().get(100));
    assertThat(gap.portfolio()).isNull();
    assertThat(gap.msciWorld()).isEqualTo(hundred());
    assertThat(risk.curve())
        .as("a benchmark with an insufficient history is left out")
        .allSatisfy(point -> assertThat(point.cac40()).isNull());
  }

  @Test
  void withoutKeptLinesTheRiskIsExplicitlyUnavailable() {
    var msci = series(msciWorld, i -> "100", Set.of());
    series(recent, i -> "20", rangeTo(YearWindow.SESSIONS - 10));

    PortfolioRisk tooRecent = PortfolioRisk.of(List.of(line(recent, 1, "20")), year(msci, msci));
    PortfolioRisk empty = PortfolioRisk.of(List.of(), year(msci, msci));
    PortfolioRisk noCloses = PortfolioRisk.withoutCloses(List.of(line(unpriced, 1, null)));

    assertThat(tooRecent.volatility()).isEqualTo(Indicator.INSUFFICIENT_HISTORY);
    assertThat(tooRecent.curve()).isEmpty();
    assertThat(empty.volatility()).isEqualTo(Indicator.NOT_COMPUTABLE);
    assertThat(empty.maxDrawdown()).isEqualTo(Indicator.NOT_COMPUTABLE);
    assertThat(noCloses.exclusions()).containsValue(LineExclusion.NOT_VALUED);
    assertThat(noCloses.volatility()).isEqualTo(Indicator.NOT_COMPUTABLE);
  }

  private static Set<Integer> rangeTo(int end) {
    return IntStream.range(0, end).boxed().collect(java.util.stream.Collectors.toSet());
  }

  private static BigDecimal hundred() {
    return new BigDecimal("100.00");
  }

  private static Indicator of(String value) {
    return Indicator.of(new BigDecimal(value));
  }

  private static Indicator notComputable() {
    return Indicator.NOT_COMPUTABLE;
  }
}
