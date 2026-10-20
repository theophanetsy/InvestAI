package dev.investai.market.api;

import dev.investai.market.application.MarketQueries.InstrumentDetails;
import dev.investai.market.domain.DailyClose;
import dev.investai.market.domain.Instrument;
import dev.investai.market.domain.InstrumentType;
import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * An instrument with its latest known close.
 *
 * @param id identifier
 * @param isin ISIN
 * @param mic venue (MIC)
 * @param ticker exchange ticker
 * @param name name
 * @param type kind of instrument
 * @param currency trading currency (ISO 4217)
 * @param trackedSince when it started being followed; absent when not tracked
 * @param historyLoaded whether one year of closes has been loaded
 * @param lastClose most recent close; absent when none is stored yet
 * @param peaEligibility PEA eligibility
 */
public record InstrumentDetailsResponse(
    UUID id,
    String isin,
    String mic,
    String ticker,
    String name,
    InstrumentType type,
    String currency,
    @Nullable Instant trackedSince,
    boolean historyLoaded,
    @Nullable DailyCloseResponse lastClose,
    PeaEligibilityResponse peaEligibility) {

  static InstrumentDetailsResponse from(InstrumentDetails details) {
    Instrument instrument = details.instrument();
    DailyClose lastClose = details.lastClose();
    return new InstrumentDetailsResponse(
        instrument.id().value(),
        instrument.listing().isin().value(),
        instrument.listing().mic().value(),
        instrument.ticker(),
        instrument.name(),
        instrument.type(),
        instrument.currency().getCurrencyCode(),
        instrument.trackedSince(),
        instrument.historyLoaded(),
        lastClose == null ? null : DailyCloseResponse.from(lastClose),
        PeaEligibilityResponse.from(instrument.peaEligibility()));
  }
}
