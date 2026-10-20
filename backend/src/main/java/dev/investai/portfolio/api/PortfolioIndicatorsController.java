package dev.investai.portfolio.api;

import dev.investai.portfolio.application.PortfolioQueries;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Indicators of the declared PEA (spec 2.4, criteria 7 to 9), computed on demand from the stored
 * closes: no provider is called, nothing is written.
 */
@RestController
@Tag(name = "portfolio")
@RequestMapping("/api/v1/portfolio/indicators")
public class PortfolioIndicatorsController {

  private final PortfolioQueries queries;

  /**
   * Creates the controller.
   *
   * @param queries the declared PEA
   */
  public PortfolioIndicatorsController(PortfolioQueries queries) {
    this.queries = queries;
  }

  /**
   * Computes the indicators of the declared PEA: composition, risk of the current portfolio as if
   * held for a year, one-year curve against the MSCI World and CAC 40 ETFs, correlations between
   * the lines, indicators of each line.
   *
   * @return the indicators
   */
  @GetMapping
  public PortfolioIndicatorsResponse getPortfolioIndicators() {
    return PortfolioIndicatorsResponse.from(queries.indicators());
  }
}
