package dev.investai.portfolio.application;

import dev.investai.market.application.InstrumentTracking;
import dev.investai.market.application.MarketQueries;
import dev.investai.market.domain.Instrument;
import dev.investai.market.domain.InstrumentId;
import dev.investai.portfolio.domain.Amounts;
import dev.investai.portfolio.domain.Cash;
import dev.investai.portfolio.domain.PortfolioLine;
import dev.investai.portfolio.domain.PortfolioLineId;
import dev.investai.portfolio.domain.PortfolioRepository;
import dev.investai.shared.domain.BusinessRuleException;
import dev.investai.shared.domain.ResourceNotFoundException;
import dev.investai.shared.logging.LogSanitizer;
import java.math.BigDecimal;
import java.time.Clock;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Changes the declared PEA (spec 2.3, criteria 3 to 5): lines copied from the broker, and cash.
 * Calls the market module through its application services only, never a provider.
 */
@Service
public class PortfolioCommands {

  /** Error code of an unknown line. */
  public static final String LINE_NOT_FOUND = "portfolio.line.not-found";

  /** Error code of a second line for the same instrument. */
  public static final String DUPLICATE_INSTRUMENT = "portfolio.line.duplicate-instrument";

  /** Error code of an instrument not traded in euros. */
  public static final String CURRENCY_NOT_SUPPORTED = "portfolio.line.currency-not-supported";

  private static final Logger LOG = LoggerFactory.getLogger(PortfolioCommands.class);

  private final PortfolioRepository portfolio;
  private final MarketQueries market;
  private final InstrumentTracking tracking;
  private final Clock clock;

  /**
   * Creates the use cases.
   *
   * @param portfolio the declared PEA
   * @param market instruments of the market module
   * @param tracking follows the held instruments
   * @param clock current time
   */
  public PortfolioCommands(
      PortfolioRepository portfolio,
      MarketQueries market,
      InstrumentTracking tracking,
      Clock clock) {
    this.portfolio = portfolio;
    this.market = market;
    this.tracking = tracking;
    this.clock = clock;
  }

  /**
   * Adds a line and follows its instrument (one year of history and catch-up of missed sessions,
   * loaded later by the market jobs: no external call here).
   *
   * @param instrumentId the instrument
   * @param quantity number of units held
   * @param averagePrice average purchase price shown by the broker, {@code null} when not given
   * @return the new line
   * @throws ResourceNotFoundException {@code instrument.not-found} when the instrument is unknown
   * @throws BusinessRuleException {@value #DUPLICATE_INSTRUMENT} or {@value
   *     #CURRENCY_NOT_SUPPORTED}
   */
  @Transactional
  public PortfolioLine addLine(
      InstrumentId instrumentId, long quantity, @Nullable BigDecimal averagePrice) {
    Instrument instrument = market.details(instrumentId).instrument();
    if (!Amounts.EUR.equals(instrument.currency())) {
      throw new BusinessRuleException(
          CURRENCY_NOT_SUPPORTED, "Instrument not traded in euros: " + instrumentId);
    }
    var line =
        new PortfolioLine(
            PortfolioLineId.newId(), instrumentId, quantity, averagePrice, clock.instant());
    if (!portfolio.insert(line)) {
      throw new BusinessRuleException(
          DUPLICATE_INSTRUMENT, "Instrument already in the portfolio: " + instrumentId);
    }
    tracking.track(instrumentId);
    String lineId = LogSanitizer.oneLine(line.id());
    String instrumentText = LogSanitizer.oneLine(instrumentId);
    LOG.info("Portfolio line added: line={}, instrument={}", lineId, instrumentText);
    return line;
  }

  /**
   * Replaces the quantity and the average price of a line.
   *
   * @param id the line
   * @param quantity number of units held
   * @param averagePrice average purchase price, {@code null} when not given
   * @return the updated line
   * @throws ResourceNotFoundException {@value #LINE_NOT_FOUND}
   */
  @Transactional
  public PortfolioLine updateLine(
      PortfolioLineId id, long quantity, @Nullable BigDecimal averagePrice) {
    PortfolioLine updated =
        portfolio
            .findLine(id)
            .orElseThrow(() -> lineNotFound(id))
            .update(quantity, averagePrice, clock.instant());
    if (!portfolio.update(updated)) {
      throw lineNotFound(id);
    }
    String lineId = LogSanitizer.oneLine(id);
    LOG.info("Portfolio line updated: line={}", lineId);
    return updated;
  }

  /**
   * Removes a line; its instrument stays followed by the market module.
   *
   * @param id the line
   * @throws ResourceNotFoundException {@value #LINE_NOT_FOUND}
   */
  @Transactional
  public void removeLine(PortfolioLineId id) {
    if (!portfolio.delete(id)) {
      throw lineNotFound(id);
    }
    String lineId = LogSanitizer.oneLine(id);
    LOG.info("Portfolio line removed: line={}", lineId);
  }

  /**
   * Declares the cash available on the PEA (idempotent).
   *
   * @param amount non-negative amount in euros
   * @return the stored cash
   */
  @Transactional
  public Cash setCash(BigDecimal amount) {
    var cash = new Cash(amount, clock.instant());
    portfolio.saveCash(cash);
    LOG.info("Portfolio cash declared");
    return cash;
  }

  private static ResourceNotFoundException lineNotFound(PortfolioLineId id) {
    return new ResourceNotFoundException(LINE_NOT_FOUND, "Unknown portfolio line: " + id);
  }
}
