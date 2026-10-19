package dev.investai.portfolio.application;

import dev.investai.market.application.MarketQueries;
import dev.investai.market.application.MarketQueries.InstrumentDetails;
import dev.investai.market.domain.DailyClose;
import dev.investai.market.domain.Instrument;
import dev.investai.market.domain.InstrumentId;
import dev.investai.portfolio.domain.Amounts;
import dev.investai.portfolio.domain.PortfolioLine;
import dev.investai.portfolio.domain.PortfolioRepository;
import dev.investai.portfolio.domain.PricedLine;
import dev.investai.portfolio.domain.Quote;
import dev.investai.portfolio.domain.Valuation;
import dev.investai.shared.logging.LogSanitizer;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
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

  /**
   * Creates the queries.
   *
   * @param portfolio the declared PEA
   * @param market instruments and closes of the market module
   */
  public PortfolioQueries(PortfolioRepository portfolio, MarketQueries market) {
    this.portfolio = portfolio;
    this.market = market;
  }

  /**
   * Values the declared PEA; lines sorted by instrument name.
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
                    String.CASE_INSENSITIVE_ORDER))
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

  private static String nameOf(Map<InstrumentId, Instrument> instruments, PortfolioLine line) {
    Instrument instrument = instruments.get(line.instrumentId());
    return instrument == null ? "" : instrument.name();
  }
}
