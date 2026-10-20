package dev.investai.portfolio.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * Valuation of the declared PEA on the latest known closes (spec 2.3, criterion 6), in euros,
 * {@link BigDecimal} rounded half-even to the cent:
 *
 * <ul>
 *   <li>value of a line = quantity × latest close, rounded once;
 *   <li>unrealized gain = quantity × (close − average price), rounded once (it may differ by a cent
 *       from the difference of two rounded amounts);
 *   <li>total = sum of the rounded values + cash; lines without close are left out and counted;
 *   <li>weight = value / total, in percent at scale 2 (the weights may not add up to exactly 100).
 * </ul>
 *
 * @param lines the valued lines, in the order given
 * @param cash the declared cash, {@code null} when not declared
 * @param cashWeightPercent share of the cash in the total, {@code null} without cash or total
 * @param total value of the priced lines plus the cash, at scale 2
 * @param unpricedLines number of lines without any known close, left out of the total
 */
public record Valuation(
    List<ValuedLine> lines,
    @Nullable Cash cash,
    @Nullable BigDecimal cashWeightPercent,
    BigDecimal total,
    int unpricedLines) {

  private static final int PERCENT_SCALE = 2;

  /** Defensive copy. */
  public Valuation {
    lines = List.copyOf(lines);
  }

  /**
   * Values the declared lines and the cash.
   *
   * @param lines the declared lines with their latest close
   * @param cash the declared cash, {@code null} when not declared
   * @return the valuation
   */
  public static Valuation of(List<PricedLine> lines, @Nullable Cash cash) {
    BigDecimal total = cash == null ? BigDecimal.ZERO : cash.amount();
    int unpriced = 0;
    for (PricedLine line : lines) {
      Quote quote = line.quote();
      if (quote == null) {
        unpriced++;
      } else {
        total = total.add(valueOf(line.line(), quote));
      }
    }
    BigDecimal roundedTotal = Amounts.toCents(total);
    List<ValuedLine> valued = lines.stream().map(line -> value(line, roundedTotal)).toList();
    BigDecimal cashWeight = cash == null ? null : weight(cash.amount(), roundedTotal);
    return new Valuation(valued, cash, cashWeight, roundedTotal, unpriced);
  }

  /**
   * Tells whether every line could be valued.
   *
   * @return {@code false} when the total leaves out lines without close
   */
  public boolean complete() {
    return unpricedLines == 0;
  }

  private static ValuedLine value(PricedLine priced, BigDecimal total) {
    PortfolioLine line = priced.line();
    Quote quote = priced.quote();
    if (quote == null) {
      return new ValuedLine(line, null, null, null, null, null);
    }
    BigDecimal value = valueOf(line, quote);
    BigDecimal averagePrice = line.averagePrice();
    BigDecimal gain = null;
    BigDecimal gainPercent = null;
    if (averagePrice != null) {
      BigDecimal difference = quote.close().subtract(averagePrice);
      gain = Amounts.toCents(difference.multiply(BigDecimal.valueOf(line.quantity())));
      gainPercent =
          difference
              .multiply(Amounts.HUNDRED)
              .divide(averagePrice, PERCENT_SCALE, RoundingMode.HALF_EVEN);
    }
    return new ValuedLine(line, quote, value, gain, gainPercent, weight(value, total));
  }

  private static BigDecimal valueOf(PortfolioLine line, Quote quote) {
    return Amounts.toCents(quote.close().multiply(BigDecimal.valueOf(line.quantity())));
  }

  private static @Nullable BigDecimal weight(BigDecimal amount, BigDecimal total) {
    return total.signum() == 0
        ? null
        : amount.multiply(Amounts.HUNDRED).divide(total, PERCENT_SCALE, RoundingMode.HALF_EVEN);
  }
}
