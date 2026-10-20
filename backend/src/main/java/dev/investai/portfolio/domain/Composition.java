package dev.investai.portfolio.domain;

import dev.investai.market.domain.Indicator;
import dev.investai.market.domain.InstrumentId;
import dev.investai.market.domain.InstrumentType;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/**
 * Composition of the declared PEA on its valuation (spec 2.4, criterion 7). Shares of the total
 * (cash included) in percent at scale 2, like the weights of the lines; {@link
 * Indicator#NOT_COMPUTABLE} when the total is zero or, for the cash, when it is not declared.
 *
 * @param lineCount number of declared lines, valued or not
 * @param largestLinePercent weight of the largest line in the total
 * @param herfindahlIndex sum of the squared weights of the valued lines within the invested part
 *     (cash excluded), at scale 4: 1 for a single line, 1/n for n equal lines
 * @param cashPercent share of the cash
 * @param etfPercent share of the ETFs
 * @param stockPercent share of the shares
 * @param otherPercent share of the other funds and instruments
 */
public record Composition(
    int lineCount,
    Indicator largestLinePercent,
    Indicator herfindahlIndex,
    Indicator cashPercent,
    Indicator etfPercent,
    Indicator stockPercent,
    Indicator otherPercent) {

  private static final int PERCENT_SCALE = 2;
  private static final int INDEX_SCALE = 4;

  /**
   * Computes the composition of a valuation.
   *
   * @param valuation the valued lines and cash
   * @param types kind of instrument of each line
   * @return the composition
   */
  public static Composition of(Valuation valuation, Map<InstrumentId, InstrumentType> types) {
    List<ValuedLine> valued = valuation.lines().stream().filter(ValuedLine::priced).toList();
    return new Composition(
        valuation.lines().size(),
        valued.stream()
            .map(ValuedLine::weightPercent)
            .filter(Objects::nonNull)
            .max(Comparator.naturalOrder())
            .map(Indicator::of)
            .orElse(Indicator.NOT_COMPUTABLE),
        herfindahl(valued),
        indicator(valuation.cashWeightPercent()),
        share(valuation, valued, types, Set.of(InstrumentType.ETF)),
        share(valuation, valued, types, Set.of(InstrumentType.STOCK)),
        share(valuation, valued, types, Set.of(InstrumentType.FUND, InstrumentType.OTHER)));
  }

  private static Indicator herfindahl(List<ValuedLine> valued) {
    List<BigDecimal> values =
        valued.stream().map(ValuedLine::value).filter(Objects::nonNull).toList();
    BigDecimal invested = values.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
    if (invested.signum() == 0) {
      return Indicator.NOT_COMPUTABLE;
    }
    BigDecimal squares =
        values.stream()
            .map(value -> value.multiply(value))
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    return Indicator.of(
        squares.divide(invested.multiply(invested), INDEX_SCALE, RoundingMode.HALF_EVEN));
  }

  private static Indicator share(
      Valuation valuation,
      List<ValuedLine> valued,
      Map<InstrumentId, InstrumentType> types,
      Set<InstrumentType> kinds) {
    if (valuation.total().signum() == 0) {
      return Indicator.NOT_COMPUTABLE;
    }
    BigDecimal amount =
        valued.stream()
            .filter(line -> kinds.contains(typeOf(types, line)))
            .map(ValuedLine::value)
            .filter(Objects::nonNull)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    return Indicator.of(
        amount
            .multiply(Amounts.HUNDRED)
            .divide(valuation.total(), PERCENT_SCALE, RoundingMode.HALF_EVEN));
  }

  /** The schema forbids a line without instrument; an unknown type counts as other. */
  private static InstrumentType typeOf(Map<InstrumentId, InstrumentType> types, ValuedLine line) {
    return Objects.requireNonNullElse(types.get(line.line().instrumentId()), InstrumentType.OTHER);
  }

  private static Indicator indicator(@Nullable BigDecimal value) {
    return value == null ? Indicator.NOT_COMPUTABLE : Indicator.of(value);
  }
}
