package dev.investai.market.api;

import dev.investai.market.application.IndicatorQueries;
import dev.investai.market.domain.InstrumentId;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Indicators computed daily on raw closes (spec 2.4, criteria 6 and 9): those of one instrument,
 * and a screening of every Paris instrument. Both read the stored table: nothing is computed during
 * the request.
 */
@RestController
@Tag(name = "market")
@RequestMapping("/api/v1/market")
public class IndicatorController {

  private final IndicatorQueries queries;

  /**
   * Creates the controller.
   *
   * @param queries stored indicators
   */
  public IndicatorController(IndicatorQueries queries) {
    this.queries = queries;
  }

  /**
   * Returns the indicators of an instrument as of the latest computation.
   *
   * @param id the instrument
   * @return the indicators; 404 {@code instrument.not-found} when unknown, {@code
   *     indicators.not-computed} before the first computation that saw it
   */
  @GetMapping("/instruments/{id}/indicators")
  public InstrumentIndicatorsResponse getInstrumentIndicators(@PathVariable UUID id) {
    return InstrumentIndicatorsResponse.from(queries.instrument(new InstrumentId(id)));
  }

  /**
   * Screens every Paris instrument on its stored indicators. An instrument whose filtered indicator
   * is not available never matches; sorts put the instruments without the indicator last.
   *
   * @param request filters, order and limit
   * @return the matching instruments; 400 {@code validation.failed} for an invalid parameter
   */
  @GetMapping("/indicators")
  public ScreeningResponse screenInstruments(@ParameterObject @Valid ScreeningRequest request) {
    return ScreeningResponse.from(queries.screen(request.toCriteria()));
  }
}
