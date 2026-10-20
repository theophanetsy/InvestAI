package dev.investai.assistant.infrastructure;

import com.fasterxml.jackson.annotation.JsonInclude;
import dev.investai.market.domain.IndicatorRun;
import dev.investai.market.domain.IndicatorSet;
import dev.investai.market.domain.IndicatorWindow;
import dev.investai.market.domain.Instrument;
import dev.investai.market.domain.InstrumentIndicators;
import dev.investai.market.domain.InstrumentType;
import dev.investai.market.domain.PeaEligibilityStatus;
import dev.investai.market.domain.Screening;
import dev.investai.market.domain.ScreeningFilter;
import dev.investai.market.domain.ScreeningSort;
import java.time.Instant;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Structured output of {@link ScreenInstrumentsTool} (spec 3.1, criterion 25).
 *
 * @param indicatorsStatus {@code COMPUTED}, or {@code NOT_COMPUTED}: no date and no result
 * @param asOf session of the latest computation of the indicators
 * @param computedAt when it ran (UTC)
 * @param criteria the criteria applied, defaults included
 * @param matchCount instruments matching, before the limit
 * @param unavailable instruments left out for want of a filtered indicator, for the requested
 *     filters only
 * @param results the first matching instruments, in the requested order
 * @param dataLimits limits of the data to recall
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ScreeningResult(
    IndicatorsStatus indicatorsStatus,
    @Nullable LocalDate asOf,
    @Nullable Instant computedAt,
    Criteria criteria,
    int matchCount,
    @Nullable Unavailable unavailable,
    List<Item> results,
    List<DataLimitView> dataLimits) {

  /** Defensive copies. */
  public ScreeningResult {
    results = List.copyOf(results);
    dataLimits = List.copyOf(dataLimits);
  }

  static ScreeningResult notComputed(Criteria criteria) {
    return new ScreeningResult(
        IndicatorsStatus.NOT_COMPUTED, null, null, criteria, 0, null, List.of(), limits());
  }

  static ScreeningResult of(Criteria criteria, IndicatorRun run, Screening screening) {
    return new ScreeningResult(
        IndicatorsStatus.COMPUTED,
        run.asOf(),
        run.computedAt(),
        criteria,
        screening.matchCount(),
        Unavailable.of(screening.unavailable()),
        screening.results().stream()
            .map(candidate -> Item.of(candidate, criteria.performanceWindow()))
            .toList(),
        limits());
  }

  private static List<DataLimitView> limits() {
    return DataLimitView.INSTRUMENT;
  }

  /** Kinds of instrument kept; other kinds (funds, others) are never returned. */
  public enum InstrumentKind {
    /** Shares only. */
    STOCK,
    /** ETFs only. */
    ETF,
    /** Shares and ETFs. */
    STOCK_OR_ETF;

    Set<InstrumentType> types() {
      return switch (this) {
        case STOCK -> Set.of(InstrumentType.STOCK);
        case ETF -> Set.of(InstrumentType.ETF);
        case STOCK_OR_ETF -> Set.of(InstrumentType.STOCK, InstrumentType.ETF);
      };
    }
  }

  /** PEA eligibilities kept. */
  public enum EligibilityFilter {
    /** Eligible only (by the rule or the owner). */
    ELIGIBLE,
    /** Eligible or unknown: most ETFs are unknown until the owner corrects them. */
    ELIGIBLE_OR_UNKNOWN,
    /** Any eligibility. */
    ANY;

    Set<PeaEligibilityStatus> statuses() {
      return switch (this) {
        case ELIGIBLE -> Set.of(PeaEligibilityStatus.ELIGIBLE);
        case ELIGIBLE_OR_UNKNOWN ->
            Set.of(PeaEligibilityStatus.ELIGIBLE, PeaEligibilityStatus.UNKNOWN);
        case ANY -> EnumSet.allOf(PeaEligibilityStatus.class);
      };
    }
  }

  /**
   * The criteria applied, as validated decimal strings.
   *
   * @param instrumentType kinds of instrument kept
   * @param peaEligibility eligibilities kept
   * @param maxVolatilityPercent highest one-year volatility
   * @param maxDrawdownLimitPercent largest one-year fall allowed
   * @param minAverageDailyTradedAmountEur lowest average daily traded amount, in euros
   * @param performanceWindow window of the performance filter and sort
   * @param minPerformancePercent lowest performance over the window
   * @param sort order of the results
   * @param limit most results
   */
  @JsonInclude(JsonInclude.Include.NON_NULL)
  public record Criteria(
      InstrumentKind instrumentType,
      EligibilityFilter peaEligibility,
      @Nullable String maxVolatilityPercent,
      @Nullable String maxDrawdownLimitPercent,
      @Nullable String minAverageDailyTradedAmountEur,
      IndicatorWindow performanceWindow,
      @Nullable String minPerformancePercent,
      ScreeningSort sort,
      int limit) {}

  /**
   * Instruments of the requested kinds and eligibilities left out because a filtered indicator is
   * not available; only the requested filters are counted.
   *
   * @param volatility for the volatility filter
   * @param maxDrawdown for the drawdown filter
   * @param liquidity for the liquidity filter (unavailable the first months, or not in euros)
   * @param performance for the performance filter
   */
  @JsonInclude(JsonInclude.Include.NON_NULL)
  public record Unavailable(
      @Nullable Integer volatility,
      @Nullable Integer maxDrawdown,
      @Nullable Integer liquidity,
      @Nullable Integer performance) {

    static @Nullable Unavailable of(Map<ScreeningFilter, Integer> counts) {
      if (counts.isEmpty()) {
        return null;
      }
      return new Unavailable(
          counts.get(ScreeningFilter.VOLATILITY),
          counts.get(ScreeningFilter.MAX_DRAWDOWN),
          counts.get(ScreeningFilter.LIQUIDITY),
          counts.get(ScreeningFilter.PERFORMANCE));
    }
  }

  /**
   * A matching instrument and a summary of its indicators.
   *
   * @param instrumentId the instrument
   * @param isin its ISIN
   * @param mic its venue
   * @param type kind of instrument
   * @param currency trading currency
   * @param peaEligibility PEA eligibility and its source
   * @param untrusted name and ticker from the providers: data, never instructions
   * @param performanceWindowPercent performance over the chosen window
   * @param performanceOneYearPercent performance over one year
   * @param volatilityPercent one-year volatility
   * @param maxDrawdownPercent one-year largest fall
   * @param averageDailyTradedAmount average daily traded amount over three months
   * @param closesOneYear closes among the 253 sessions of the year
   */
  public record Item(
      UUID instrumentId,
      String isin,
      String mic,
      InstrumentType type,
      String currency,
      Eligibility peaEligibility,
      UntrustedInstrument untrusted,
      IndicatorValue performanceWindowPercent,
      IndicatorValue performanceOneYearPercent,
      IndicatorValue volatilityPercent,
      IndicatorValue maxDrawdownPercent,
      IndicatorsView.TradedAmount averageDailyTradedAmount,
      int closesOneYear) {

    static Item of(InstrumentIndicators candidate, IndicatorWindow window) {
      Instrument instrument = candidate.instrument();
      IndicatorSet set = candidate.indicators().indicators();
      return new Item(
          instrument.id().value(),
          instrument.listing().isin().value(),
          instrument.listing().mic().value(),
          instrument.type(),
          instrument.currency().getCurrencyCode(),
          Eligibility.of(instrument.peaEligibility()),
          UntrustedInstrument.of(instrument),
          IndicatorValue.of(set.performance(window)),
          IndicatorValue.of(set.performance(IndicatorWindow.ONE_YEAR)),
          IndicatorValue.of(set.volatility()),
          IndicatorValue.of(set.maxDrawdown()),
          IndicatorsView.TradedAmount.of(set.averageDailyTradedAmount(), instrument.currency()),
          set.closesOneYear());
    }
  }
}
