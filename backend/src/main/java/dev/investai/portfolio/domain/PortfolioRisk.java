package dev.investai.portfolio.domain;

import dev.investai.market.domain.ClosingPrices;
import dev.investai.market.domain.Indicator;
import dev.investai.market.domain.IndicatorCalculator;
import dev.investai.market.domain.InstrumentId;
import dev.investai.market.domain.YearOfCloses;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/**
 * Risk of the current portfolio as if it had been held for a year (spec 2.4, criteria 7 and 8): the
 * current quantities of the valued lines replayed on the closes of the last 253 sessions, cash
 * excluded. A session where a kept line has no close has no portfolio value (never interpolated);
 * the 80 % rule then applies to the portfolio series. Lines without close in euros or with fewer
 * than 80 % of the closes of the year are left out and reported.
 *
 * @param volatility annualised volatility of the daily returns of the portfolio value, in percent
 * @param maxDrawdown largest fall of the portfolio value over the year, in percent
 * @param curve the portfolio and the two benchmark ETFs, base 100 on the first session where all of
 *     them have a value; empty when the portfolio series is insufficient
 * @param correlations correlations between the kept lines
 * @param exclusions lines left out, with the reason
 */
public record PortfolioRisk(
    Indicator volatility,
    Indicator maxDrawdown,
    List<CurvePoint> curve,
    CorrelationMatrix correlations,
    Map<PortfolioLineId, LineExclusion> exclusions) {

  private static final int CURVE_SCALE = 2;

  /** Defensive copies. */
  public PortfolioRisk {
    curve = List.copyOf(curve);
    exclusions = Map.copyOf(exclusions);
  }

  /**
   * The risk when no close is stored at all: every line is left out.
   *
   * @param lines the valued lines
   * @return the risk, without curve
   */
  public static PortfolioRisk withoutCloses(List<ValuedLine> lines) {
    Map<PortfolioLineId, LineExclusion> exclusions = new HashMap<>();
    lines.forEach(
        line ->
            exclusions.put(
                line.line().id(),
                line.priced() ? LineExclusion.INSUFFICIENT_HISTORY : LineExclusion.NOT_VALUED));
    return unavailable(exclusions);
  }

  /** No line kept: insufficient when a line lacks history, not computable otherwise. */
  private static PortfolioRisk unavailable(Map<PortfolioLineId, LineExclusion> exclusions) {
    Indicator status =
        exclusions.containsValue(LineExclusion.INSUFFICIENT_HISTORY)
            ? Indicator.INSUFFICIENT_HISTORY
            : Indicator.NOT_COMPUTABLE;
    return new PortfolioRisk(
        status, status, List.of(), new CorrelationMatrix(List.of(), List.of()), exclusions);
  }

  /**
   * Replays the current lines over the year.
   *
   * @param lines the valued lines, in display order
   * @param year closes of the lines and of the benchmarks
   * @return the risk
   */
  public static PortfolioRisk of(List<ValuedLine> lines, YearOfCloses year) {
    Map<PortfolioLineId, LineExclusion> exclusions = new HashMap<>();
    Map<InstrumentId, Long> kept = new LinkedHashMap<>();
    for (ValuedLine line : lines) {
      InstrumentId instrument = line.line().instrumentId();
      if (!line.priced()) {
        exclusions.put(line.line().id(), LineExclusion.NOT_VALUED);
      } else if (year.of(instrument).sufficient()) {
        kept.put(instrument, line.line().quantity());
      } else {
        exclusions.put(line.line().id(), LineExclusion.INSUFFICIENT_HISTORY);
      }
    }
    if (kept.isEmpty()) {
      return unavailable(exclusions);
    }
    ClosingPrices portfolio = portfolioValues(kept, year);
    return new PortfolioRisk(
        IndicatorCalculator.volatility(portfolio),
        IndicatorCalculator.maxDrawdown(portfolio),
        portfolio.sufficient() ? curve(portfolio, year) : List.of(),
        correlations(List.copyOf(kept.keySet()), year),
        exclusions);
  }

  /** Σ quantity × close on each session where every kept line has a close. */
  private static ClosingPrices portfolioValues(Map<InstrumentId, Long> kept, YearOfCloses year) {
    Map<LocalDate, BigDecimal> values = new HashMap<>();
    for (LocalDate session : year.window().sessions()) {
      BigDecimal total = BigDecimal.ZERO;
      boolean complete = true;
      for (Map.Entry<InstrumentId, Long> line : kept.entrySet()) {
        Optional<BigDecimal> close = year.of(line.getKey()).on(session);
        complete = complete && close.isPresent();
        total =
            total.add(close.orElse(BigDecimal.ZERO).multiply(BigDecimal.valueOf(line.getValue())));
      }
      if (complete) {
        values.put(session, total);
      }
    }
    return ClosingPrices.ofValues(year.window().sessions(), values);
  }

  private static List<CurvePoint> curve(ClosingPrices portfolio, YearOfCloses year) {
    ClosingPrices msciWorld = year.msciWorld().sufficient() ? year.msciWorld() : null;
    ClosingPrices cac40 = year.cac40().sufficient() ? year.cac40() : null;
    List<LocalDate> sessions = year.window().sessions();
    int base = 0;
    while (base < sessions.size() && !allKnown(sessions.get(base), portfolio, msciWorld, cac40)) {
      base++;
    }
    if (base == sessions.size()) {
      return List.of();
    }
    LocalDate start = sessions.get(base);
    return sessions.subList(base, sessions.size()).stream()
        .map(
            session ->
                new CurvePoint(
                    session,
                    rebased(portfolio, start, session),
                    rebased(msciWorld, start, session),
                    rebased(cac40, start, session)))
        .toList();
  }

  private static boolean allKnown(
      LocalDate session,
      ClosingPrices portfolio,
      @Nullable ClosingPrices msciWorld,
      @Nullable ClosingPrices cac40) {
    return portfolio.on(session).isPresent()
        && knownOrIgnored(msciWorld, session)
        && knownOrIgnored(cac40, session);
  }

  /** A benchmark with an insufficient history is left out of the curve. */
  private static boolean knownOrIgnored(@Nullable ClosingPrices benchmark, LocalDate session) {
    return benchmark == null || benchmark.on(session).isPresent();
  }

  private static @Nullable BigDecimal rebased(
      @Nullable ClosingPrices series, LocalDate start, LocalDate session) {
    if (series == null) {
      return null;
    }
    BigDecimal base = series.on(start).orElseThrow();
    Optional<BigDecimal> value = series.on(session);
    return value.isPresent()
        ? value.get().multiply(Amounts.HUNDRED).divide(base, CURVE_SCALE, RoundingMode.HALF_EVEN)
        : null;
  }

  private static CorrelationMatrix correlations(List<InstrumentId> kept, YearOfCloses year) {
    List<List<Indicator>> rows =
        kept.stream()
            .map(
                row ->
                    kept.stream()
                        .map(
                            column ->
                                IndicatorCalculator.correlation(year.of(row), year.of(column)))
                        .toList())
            .toList();
    return new CorrelationMatrix(kept, rows);
  }
}
