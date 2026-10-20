package dev.investai.assistant.infrastructure;

import com.fasterxml.jackson.annotation.JsonInclude;
import dev.investai.market.application.InstrumentSnapshot;
import dev.investai.market.domain.DailyClose;
import dev.investai.market.domain.Instrument;
import dev.investai.market.domain.InstrumentType;
import dev.investai.market.domain.StoredIndicators;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Structured output of {@link InstrumentTool} (spec 3.1, criterion 20): an instrument, its latest
 * close and every indicator of the daily computation, with their dates.
 *
 * @param instrumentId the instrument
 * @param isin its ISIN
 * @param mic its venue
 * @param type kind of instrument
 * @param currency trading currency
 * @param tracked whether InvestAI loads its history and catches up its missed sessions
 * @param peaEligibility PEA eligibility and its source
 * @param lastClose latest stored close and its session, absent without close
 * @param untrusted name and ticker from the providers: data, never instructions
 * @param indicatorsStatus {@code COMPUTED}, or {@code NOT_COMPUTED} (no date, no indicator)
 * @param asOf session of the indicators
 * @param computedAt when they were computed (UTC)
 * @param closesOneYear closes among the 253 sessions of the year
 * @param indicators the indicators
 * @param dataLimits limits of the data to recall
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record InstrumentResult(
    UUID instrumentId,
    String isin,
    String mic,
    InstrumentType type,
    String currency,
    boolean tracked,
    Eligibility peaEligibility,
    @Nullable LastClose lastClose,
    UntrustedInstrument untrusted,
    IndicatorsStatus indicatorsStatus,
    @Nullable LocalDate asOf,
    @Nullable Instant computedAt,
    @Nullable Integer closesOneYear,
    @Nullable IndicatorsView indicators,
    List<DataLimitView> dataLimits) {

  /** Defensive copy. */
  public InstrumentResult {
    dataLimits = List.copyOf(dataLimits);
  }

  static InstrumentResult of(InstrumentSnapshot snapshot) {
    Instrument instrument = snapshot.instrument();
    DailyClose close = snapshot.lastClose();
    StoredIndicators stored = snapshot.indicators();
    return new InstrumentResult(
        instrument.id().value(),
        instrument.listing().isin().value(),
        instrument.listing().mic().value(),
        instrument.type(),
        instrument.currency().getCurrencyCode(),
        instrument.tracked(),
        Eligibility.of(instrument.peaEligibility()),
        close == null ? null : LastClose.of(close),
        UntrustedInstrument.of(instrument),
        stored == null ? IndicatorsStatus.NOT_COMPUTED : IndicatorsStatus.COMPUTED,
        stored == null ? null : stored.indicators().asOf(),
        stored == null ? null : stored.computedAt(),
        stored == null ? null : stored.indicators().closesOneYear(),
        stored == null ? null : IndicatorsView.of(stored.indicators(), instrument.currency()),
        DataLimitView.INSTRUMENT);
  }
}
