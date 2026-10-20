package dev.investai.portfolio.api;

import dev.investai.portfolio.application.PortfolioCommands;
import dev.investai.portfolio.application.PortfolioQueries;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The declared PEA (spec 2.3, criteria 4 and 6): valuation and cash. Lines: {@link
 * PortfolioLineController}. Everything is read from the database, no provider is called.
 */
@RestController
@Tag(name = "portfolio")
@RequestMapping("/api/v1/portfolio")
public class PortfolioController {

  private final PortfolioQueries queries;
  private final PortfolioCommands commands;

  /**
   * Creates the controller.
   *
   * @param queries the valuation
   * @param commands changes of the declared PEA
   */
  public PortfolioController(PortfolioQueries queries, PortfolioCommands commands) {
    this.queries = queries;
    this.commands = commands;
  }

  /**
   * Values the declared PEA on the latest known closes.
   *
   * @return the lines, the cash and the total
   */
  @GetMapping
  public PortfolioResponse getPortfolio() {
    return PortfolioResponse.from(queries.overview());
  }

  /**
   * Declares the cash available on the PEA (idempotent).
   *
   * @param request the amount; 400 {@code validation.failed} when not a decimal string ≥ 0
   * @return the stored cash
   */
  @PutMapping("/cash")
  public CashResponse setPortfolioCash(@Valid @RequestBody CashRequest request) {
    return CashResponse.from(commands.setCash(InputFormats.parse(request.amount())), null);
  }
}
