package dev.investai.portfolio.application;

import dev.investai.market.domain.Instrument;
import dev.investai.market.domain.InstrumentId;
import dev.investai.portfolio.domain.PortfolioLine;
import dev.investai.portfolio.domain.Valuation;
import java.util.Map;

/**
 * The valued PEA and the instruments it holds.
 *
 * @param valuation the valuation
 * @param instruments the held instruments, by identifier
 */
public record PortfolioOverview(Valuation valuation, Map<InstrumentId, Instrument> instruments) {

  /** Defensive copy. */
  public PortfolioOverview {
    instruments = Map.copyOf(instruments);
  }

  /**
   * Returns the instrument of a line.
   *
   * @param line a line of the valuation
   * @return its instrument
   * @throws IllegalStateException when missing (the schema forbids it: foreign key)
   */
  public Instrument instrumentOf(PortfolioLine line) {
    Instrument instrument = instruments.get(line.instrumentId());
    if (instrument == null) {
      throw new IllegalStateException("Instrument of a portfolio line not found: " + line.id());
    }
    return instrument;
  }
}
