package dev.investai.market.api;

import dev.investai.market.application.InstrumentTracking;
import dev.investai.market.application.MarketQueries;
import dev.investai.market.domain.InstrumentId;
import dev.investai.market.domain.InvalidPriceRangeException;
import dev.investai.shared.api.Problems;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.ErrorResponseException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Market data read from the database only (ADR-0009): instrument search, details, closes, and
 * tracking. No endpoint calls a provider.
 */
@RestController
@Tag(name = "market")
@RequestMapping("/api/v1/market/instruments")
public class MarketController {

  private static final int MAX_QUERY_LENGTH = 100;

  private final MarketQueries queries;
  private final InstrumentTracking tracking;

  /**
   * Creates the controller.
   *
   * @param queries read side
   * @param tracking follows instruments
   */
  public MarketController(MarketQueries queries, InstrumentTracking tracking) {
    this.queries = queries;
    this.tracking = tracking;
  }

  /**
   * Searches the local reference data by exact ISIN, exact ticker or name fragment (case and
   * accents ignored); tracked instruments first, then by name, 20 results at most.
   *
   * @param query ISIN, ticker or part of the name
   * @return matching instruments
   */
  @GetMapping
  public List<InstrumentSummaryResponse> searchInstruments(
      @RequestParam @NotBlank @Size(max = MAX_QUERY_LENGTH) String query) {
    return queries.search(query).stream().map(InstrumentSummaryResponse::from).toList();
  }

  /**
   * Returns an instrument with its latest known close.
   *
   * @param id the instrument
   * @return the details; 404 {@code instrument.not-found} when unknown
   */
  @GetMapping("/{id}")
  public InstrumentDetailsResponse getInstrument(@PathVariable UUID id) {
    return InstrumentDetailsResponse.from(queries.details(new InstrumentId(id)));
  }

  /**
   * Lists the raw daily closes of a period, by ascending date. Sessions without trade have no
   * close.
   *
   * @param id the instrument
   * @param from first session (ISO date), default one year before {@code to}
   * @param to last session (ISO date), default today; at most five years after {@code from}
   * @return the closes; 400 {@code validation.failed} for an invalid period
   */
  @GetMapping("/{id}/prices")
  public List<DailyCloseResponse> getInstrumentPrices(
      @PathVariable UUID id,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
          @Nullable LocalDate from,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
          @Nullable LocalDate to) {
    try {
      return queries.closes(new InstrumentId(id), from, to).stream()
          .map(DailyCloseResponse::from)
          .toList();
    } catch (InvalidPriceRangeException ex) {
      throw new ErrorResponseException(
          HttpStatus.BAD_REQUEST, Problems.invalidParameter(ex.parameter(), ex.constraint()), ex);
    }
  }

  /**
   * Follows an instrument (idempotent). Its history is loaded later by the EODHD job, never during
   * the request.
   *
   * @param id the instrument; 404 {@code instrument.not-found} when unknown
   */
  @PutMapping("/{id}/tracking")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void trackInstrument(@PathVariable UUID id) {
    tracking.track(new InstrumentId(id));
  }
}
