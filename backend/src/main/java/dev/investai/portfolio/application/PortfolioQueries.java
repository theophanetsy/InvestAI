package dev.investai.portfolio.application;

import dev.investai.market.application.IndicatorQueries;
import dev.investai.market.application.MarketQueries;
import dev.investai.market.application.MarketQueries.InstrumentDetails;
import dev.investai.market.domain.DailyClose;
import dev.investai.market.domain.IndicatorSet;
import dev.investai.market.domain.Instrument;
import dev.investai.market.domain.InstrumentId;
import dev.investai.market.domain.InstrumentType;
import dev.investai.market.domain.YearOfCloses;
import dev.investai.portfolio.domain.Amounts;
import dev.investai.portfolio.domain.Composition;
import dev.investai.portfolio.domain.PortfolioLine;
import dev.investai.portfolio.domain.PortfolioRepository;
import dev.investai.portfolio.domain.PortfolioRisk;
import dev.investai.portfolio.domain.PricedLine;
import dev.investai.portfolio.domain.Quote;
import dev.investai.portfolio.domain.Valuation;
import dev.investai.portfolio.domain.ValuedLine;
import dev.investai.shared.logging.LogSanitizer;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Read side of the declared PEA: its lines valued on the latest closes stored by the market module
 * (spec 2.3, criterion 6). Never calls a provider. Read later by the tools of the advice agent.
 */
@Service
@Transactional(readOnly = true)
public class PortfolioQueries {

  private static final Logger LOG = LoggerFactory.getLogger(PortfolioQueries.class);

  private final PortfolioRepository portfolio;
  private final MarketQueries market;
  private final IndicatorQueries indicators;

  /**
   * Creates the queries.
   *
   * @param portfolio the declared PEA
   * @param market instruments and closes of the market module
   * @param indicators closes of the year, for the indicators
   */
  public PortfolioQueries(
      PortfolioRepository portfolio, MarketQueries market, IndicatorQueries indicators) {
    this.portfolio = portfolio;
    this.market = market;
    this.indicators = indicators;
  }

  /**
   * Computes the indicators of the declared PEA on demand (spec 2.4, criteria 7 and 8): a few
   * lines, read from the stored closes in two queries, always consistent with the declared lines
   * and never computed by the daily job.
   *
   * @return the composition, the risk of the current portfolio as if held for a year, and the
   *     indicators of each held instrument
   */
  public PortfolioIndicators indicators() {
    PortfolioOverview overview = overview();
    Map<InstrumentId, InstrumentType> types =
        overview.instruments().values().stream()
            .collect(Collectors.toMap(Instrument::id, Instrument::type));
    Composition composition = Composition.of(overview.valuation(), types);
    List<ValuedLine> lines = overview.valuation().lines();
    Optional<YearOfCloses> year = indicators.yearOfCloses(overview.instruments().keySet());
    if (year.isEmpty()) {
      return new PortfolioIndicators(
          overview, null, Map.of(), composition, PortfolioRisk.withoutCloses(lines));
    }
    Map<InstrumentId, IndicatorSet> lineIndicators =
        overview.instruments().keySet().stream()
            .collect(Collectors.toMap(Function.identity(), year.get()::indicatorsOf));
    return new PortfolioIndicators(
        overview,
        year.get().window().asOf(),
        lineIndicators,
        composition,
        PortfolioRisk.of(lines, year.get()));
  }

  /**
   * Values the declared PEA; lines sorted by instrument name, then ISIN and venue.
   *
   * @return the valuation and the held instruments
   */
  public PortfolioOverview overview() {
    List<PortfolioLine> lines = portfolio.findLines();
    Map<InstrumentId, InstrumentDetails> details =
        market.details(lines.stream().map(PortfolioLine::instrumentId).toList());
    Map<InstrumentId, Instrument> instruments =
        details.values().stream()
            .map(InstrumentDetails::instrument)
            .collect(Collectors.toMap(Instrument::id, Function.identity()));
    List<PricedLine> priced =
        lines.stream()
            .map(line -> new PricedLine(line, quoteOf(details.get(line.instrumentId()))))
            .sorted(
                Comparator.comparing(
                        (PricedLine line) -> nameOf(instruments, line.line()),
                        String.CASE_INSENSITIVE_ORDER)
                    .thenComparing(line -> listingOf(instruments, line.line())))
            .toList();
    return new PortfolioOverview(
        Valuation.of(priced, portfolio.findCash().orElse(null)), instruments);
  }

  /** The latest close in euros; another currency is never mixed into a total in euros. */
  private static @Nullable Quote quoteOf(@Nullable InstrumentDetails details) {
    DailyClose close = details == null ? null : details.lastClose();
    if (close == null) {
      return null;
    }
    if (!Amounts.EUR.equals(close.currency())) {
      String instrument = LogSanitizer.oneLine(close.instrumentId());
      LOG.warn("Close not in euros left out of the valuation: instrument={}", instrument);
      return null;
    }
    return new Quote(close.close(), close.tradingDate());
  }

  private static String listingOf(Map<InstrumentId, Instrument> instruments, PortfolioLine line) {
    Instrument instrument = instruments.get(line.instrumentId());
    return instrument == null ? "" : instrument.listing().toString();
  }

  private static String nameOf(Map<InstrumentId, Instrument> instruments, PortfolioLine line) {
    Instrument instrument = instruments.get(line.instrumentId());
    return instrument == null ? "" : instrument.name();
  }
}
