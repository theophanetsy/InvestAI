package dev.investai.market.api;

import dev.investai.market.domain.Screening;
import java.util.List;

/**
 * Result of a screening of every Paris instrument on its stored indicators.
 *
 * @param matchCount instruments matching the criteria, before the limit
 * @param results the first matching instruments, in the requested order
 */
public record ScreeningResponse(int matchCount, List<InstrumentIndicatorsResponse> results) {

  /** Defensive copy. */
  public ScreeningResponse {
    results = List.copyOf(results);
  }

  static ScreeningResponse from(Screening screening) {
    return new ScreeningResponse(
        screening.matchCount(),
        screening.results().stream().map(InstrumentIndicatorsResponse::from).toList());
  }
}
