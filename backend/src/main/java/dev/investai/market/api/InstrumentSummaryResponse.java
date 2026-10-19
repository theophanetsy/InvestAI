package dev.investai.market.api;

import dev.investai.market.domain.Instrument;
import dev.investai.market.domain.InstrumentType;
import java.util.UUID;

/**
 * An instrument in search results.
 *
 * @param id identifier
 * @param isin ISIN
 * @param mic venue (MIC), e.g. {@code XPAR}, {@code ALXP}
 * @param ticker exchange ticker
 * @param name name
 * @param type kind of instrument
 * @param currency trading currency (ISO 4217)
 * @param tracked whether it is followed (one year of history and catch-up of missed sessions)
 * @param peaEligibility PEA eligibility
 */
public record InstrumentSummaryResponse(
    UUID id,
    String isin,
    String mic,
    String ticker,
    String name,
    InstrumentType type,
    String currency,
    boolean tracked,
    PeaEligibilityResponse peaEligibility) {

  /**
   * Converts an instrument; also used by the portfolio API (one type in the generated client).
   *
   * @param instrument the instrument
   * @return the summary
   */
  public static InstrumentSummaryResponse from(Instrument instrument) {
    return new InstrumentSummaryResponse(
        instrument.id().value(),
        instrument.listing().isin().value(),
        instrument.listing().mic().value(),
        instrument.ticker(),
        instrument.name(),
        instrument.type(),
        instrument.currency().getCurrencyCode(),
        instrument.tracked(),
        PeaEligibilityResponse.from(instrument.peaEligibility()));
  }
}
