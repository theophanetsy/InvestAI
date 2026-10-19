package dev.investai.portfolio.api;

import dev.investai.market.domain.InstrumentId;
import dev.investai.portfolio.application.PortfolioCommands;
import dev.investai.portfolio.domain.PortfolioLineId;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Lines of the declared PEA, copied from the broker (spec 2.3, criteria 3 and 5). */
@RestController
@Tag(name = "portfolio")
@RequestMapping("/api/v1/portfolio/lines")
public class PortfolioLineController {

  private final PortfolioCommands commands;

  /**
   * Creates the controller.
   *
   * @param commands changes of the declared PEA
   */
  public PortfolioLineController(PortfolioCommands commands) {
    this.commands = commands;
  }

  /**
   * Adds a line and follows its instrument (history loaded later by the market jobs).
   *
   * @param request the line; 404 {@code instrument.not-found}, 422 {@code
   *     portfolio.line.duplicate-instrument} or {@code portfolio.line.currency-not-supported}
   * @return the new line
   */
  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public DeclaredLineResponse addPortfolioLine(@Valid @RequestBody PortfolioLineRequest request) {
    return DeclaredLineResponse.from(
        commands.addLine(
            new InstrumentId(request.instrumentId()),
            request.quantity(),
            request.averagePriceValue()));
  }

  /**
   * Replaces the quantity and the average price of a line.
   *
   * @param id the line; 404 {@code portfolio.line.not-found} when unknown
   * @param request the new values
   * @return the updated line
   */
  @PutMapping("/{id}")
  public DeclaredLineResponse updatePortfolioLine(
      @PathVariable UUID id, @Valid @RequestBody PortfolioLineUpdateRequest request) {
    return DeclaredLineResponse.from(
        commands.updateLine(
            new PortfolioLineId(id), request.quantity(), request.averagePriceValue()));
  }

  /**
   * Removes a line; its instrument stays followed.
   *
   * @param id the line; 404 {@code portfolio.line.not-found} when unknown
   */
  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void deletePortfolioLine(@PathVariable UUID id) {
    commands.removeLine(new PortfolioLineId(id));
  }
}
