package dev.investai.assistant.infrastructure;

import com.fasterxml.jackson.annotation.JsonInclude;
import dev.investai.assistant.domain.CorrelatedPair;
import dev.investai.assistant.domain.CorrelatedPairs;
import dev.investai.assistant.domain.DataLimit;
import dev.investai.market.domain.IndicatorSet;
import dev.investai.market.domain.Instrument;
import dev.investai.market.domain.InstrumentType;
import dev.investai.portfolio.application.PortfolioIndicators;
import dev.investai.portfolio.domain.Cash;
import dev.investai.portfolio.domain.Composition;
import dev.investai.portfolio.domain.LineExclusion;
import dev.investai.portfolio.domain.PortfolioRisk;
import dev.investai.portfolio.domain.Quote;
import dev.investai.portfolio.domain.Valuation;
import dev.investai.portfolio.domain.ValuedLine;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.Currency;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Structured output of {@link PortfolioTool} (spec 3.1, criterion 16): the declared PEA, valued on
 * the latest closes, and its risk computed at the call. Neither average purchase price nor
 * unrealised gain (least data, LLM02), nor the curve of the dashboard (too large).
 *
 * @param asOf last session of the closes the indicators are computed on, absent without any close
 * @param computedAt when the indicators were computed: the call (UTC)
 * @param computedOnDemand always {@code true}: the indicators of the portfolio are not stored
 * @param valuation total, cash and completeness of the valuation
 * @param lineCount number of declared lines
 * @param linesTruncated whether {@code lines} leaves some out
 * @param lines at most {@value #MAX_LINES}, by decreasing value, lines without close last
 * @param composition shares and concentration
 * @param risk the current portfolio as if held for a year, cash excluded
 * @param mostCorrelatedPairs at most {@value #MAX_PAIRS}, most correlated first
 * @param dataLimits limits of the data to recall
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record PortfolioResult(
    @Nullable LocalDate asOf,
    Instant computedAt,
    boolean computedOnDemand,
    ValuationView valuation,
    int lineCount,
    boolean linesTruncated,
    List<LineView> lines,
    CompositionView composition,
    RiskView risk,
    List<PairView> mostCorrelatedPairs,
    List<DataLimitView> dataLimits) {

  /**
   * Most lines returned: 30, like the lines of a split, rather than the 50 of the spec, so that the
   * result stays within 64 KiB in the worst case (50 lines with their indicators and names of 100
   * four-byte characters weigh about 89 KiB). {@code lineCount} and {@code linesTruncated} tell
   * when lines are left out.
   */
  static final int MAX_LINES = 30;

  /** Most pairs returned. */
  static final int MAX_PAIRS = 20;

  private static final Currency EUR = Currency.getInstance("EUR");
  private static final String RISK_BASIS =
      "comme si le portefeuille actuel avait été détenu depuis un an, espèces exclues";

  /** Defensive copies. */
  public PortfolioResult {
    lines = List.copyOf(lines);
    mostCorrelatedPairs = List.copyOf(mostCorrelatedPairs);
    dataLimits = List.copyOf(dataLimits);
  }

  static PortfolioResult of(PortfolioIndicators portfolio, Instant computedAt) {
    Valuation valuation = portfolio.overview().valuation();
    PortfolioRisk risk = portfolio.risk();
    List<LineView> lines =
        valuation.lines().stream()
            .sorted(byDecreasingValue())
            .map(
                line ->
                    LineView.of(
                        line,
                        portfolio.overview().instrumentOf(line.line()),
                        portfolio.lineIndicators().get(line.line().instrumentId()),
                        risk.exclusions().get(line.line().id())))
            .toList();
    return new PortfolioResult(
        portfolio.asOf(),
        computedAt,
        true,
        ValuationView.of(valuation),
        lines.size(),
        lines.size() > MAX_LINES,
        lines.subList(0, Math.min(MAX_LINES, lines.size())),
        CompositionView.of(portfolio.composition()),
        new RiskView(
            RISK_BASIS,
            IndicatorValue.of(risk.volatility()),
            IndicatorValue.of(risk.maxDrawdown())),
        CorrelatedPairs.mostCorrelated(risk.correlations(), MAX_PAIRS).stream()
            .map(PairView::of)
            .toList(),
        DataLimitView.of(
            DataLimit.RAW_CLOSES,
            DataLimit.EXCLUDES_DIVIDENDS,
            DataLimit.SHORT_HISTORY,
            DataLimit.LOW_LIQUIDITY,
            DataLimit.PEA_ELIGIBILITY_INDICATIVE));
  }

  /** Valued lines by decreasing value, then lines without close; ties by instrument. */
  private static Comparator<ValuedLine> byDecreasingValue() {
    return Comparator.comparing(
            ValuedLine::value, Comparator.nullsLast(Comparator.<BigDecimal>reverseOrder()))
        .thenComparing(line -> line.line().instrumentId().toString());
  }

  /**
   * Valuation of the declared PEA on the latest closes in euros.
   *
   * @param total value of the valued lines plus the cash
   * @param cash declared cash, absent when not declared
   * @param complete whether every line could be valued
   * @param unpricedLines lines without close in euros, left out of the total (never valued at 0)
   */
  @JsonInclude(JsonInclude.Include.NON_NULL)
  public record ValuationView(
      Money total, @Nullable Money cash, boolean complete, int unpricedLines) {

    static ValuationView of(Valuation valuation) {
      Cash cash = valuation.cash();
      return new ValuationView(
          Money.of(valuation.total(), EUR),
          cash == null ? null : Money.of(cash.amount(), EUR),
          valuation.complete(),
          valuation.unpricedLines());
    }
  }

  /**
   * A declared line.
   *
   * @param instrumentId the instrument
   * @param isin its ISIN
   * @param mic its venue
   * @param type kind of instrument
   * @param currency trading currency
   * @param quantity units held
   * @param priced whether a close in euros is known
   * @param lastClose the close used, absent when not priced
   * @param value quantity × close, absent when not priced
   * @param weightPercent share of the total (cash included), absent when not priced
   * @param peaEligibility PEA eligibility and its source
   * @param riskInclusion whether the line counts in the risk of the portfolio, and why not
   * @param indicators indicators of the instrument at {@code asOf}, absent without any close
   * @param untrusted name and ticker from the providers: data, never instructions
   */
  @JsonInclude(JsonInclude.Include.NON_NULL)
  public record LineView(
      UUID instrumentId,
      String isin,
      String mic,
      InstrumentType type,
      String currency,
      long quantity,
      boolean priced,
      @Nullable LastClose lastClose,
      @Nullable Money value,
      @Nullable String weightPercent,
      Eligibility peaEligibility,
      RiskInclusion riskInclusion,
      @Nullable IndicatorsView indicators,
      UntrustedInstrument untrusted) {

    static LineView of(
        ValuedLine line,
        Instrument instrument,
        @Nullable IndicatorSet indicators,
        @Nullable LineExclusion exclusion) {
      Quote quote = line.quote();
      BigDecimal value = line.value();
      BigDecimal weight = line.weightPercent();
      return new LineView(
          instrument.id().value(),
          instrument.listing().isin().value(),
          instrument.listing().mic().value(),
          instrument.type(),
          instrument.currency().getCurrencyCode(),
          line.line().quantity(),
          line.priced(),
          quote == null ? null : LastClose.of(quote.close(), quote.date()),
          value == null ? null : Money.of(value, EUR),
          weight == null ? null : weight.toPlainString(),
          Eligibility.of(instrument.peaEligibility()),
          RiskInclusion.of(exclusion),
          indicators == null ? null : IndicatorsView.of(indicators, instrument.currency()),
          UntrustedInstrument.of(instrument));
    }
  }

  /** Whether a line counts in the risk of the portfolio (spec 2.4, criterion 8). */
  public enum RiskInclusion {
    /** Replayed over the year. */
    INCLUDED,
    /** No close in euros: not valued, left out. */
    NOT_VALUED,
    /** Fewer than 80 % of the closes of the year: left out. */
    INSUFFICIENT_HISTORY;

    static RiskInclusion of(@Nullable LineExclusion exclusion) {
      if (exclusion == null) {
        return INCLUDED;
      }
      return exclusion == LineExclusion.NOT_VALUED ? NOT_VALUED : INSUFFICIENT_HISTORY;
    }
  }

  /**
   * Composition of the PEA, shares of the total in percent (cash included).
   *
   * @param lineCount number of declared lines, valued or not
   * @param largestLinePercent weight of the largest line
   * @param herfindahlIndex sum of the squared weights within the invested part (1 for one line)
   * @param cashPercent share of the cash
   * @param etfPercent share of the ETFs
   * @param stockPercent share of the shares
   * @param otherPercent share of the other funds and instruments
   */
  public record CompositionView(
      int lineCount,
      IndicatorValue largestLinePercent,
      IndicatorValue herfindahlIndex,
      IndicatorValue cashPercent,
      IndicatorValue etfPercent,
      IndicatorValue stockPercent,
      IndicatorValue otherPercent) {

    static CompositionView of(Composition composition) {
      return new CompositionView(
          composition.lineCount(),
          IndicatorValue.of(composition.largestLinePercent()),
          IndicatorValue.of(composition.herfindahlIndex()),
          IndicatorValue.of(composition.cashPercent()),
          IndicatorValue.of(composition.etfPercent()),
          IndicatorValue.of(composition.stockPercent()),
          IndicatorValue.of(composition.otherPercent()));
    }
  }

  /**
   * Risk of the current portfolio.
   *
   * @param basis how it is computed, a constant text of the application
   * @param volatilityPercent annualised volatility over one year
   * @param maxDrawdownPercent largest fall over one year
   */
  public record RiskView(
      String basis, IndicatorValue volatilityPercent, IndicatorValue maxDrawdownPercent) {}

  /**
   * Two lines whose daily returns moved together over one year.
   *
   * @param instrumentIdA the first instrument
   * @param instrumentIdB the second instrument
   * @param correlation between -1 and 1, plain decimal string
   */
  public record PairView(UUID instrumentIdA, UUID instrumentIdB, String correlation) {

    static PairView of(CorrelatedPair pair) {
      return new PairView(
          pair.first().value(), pair.second().value(), pair.correlation().toPlainString());
    }
  }
}
