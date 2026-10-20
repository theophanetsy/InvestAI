package dev.investai.portfolio.api;

import dev.investai.portfolio.application.PortfolioOverview;
import dev.investai.portfolio.domain.Amounts;
import dev.investai.portfolio.domain.Cash;
import dev.investai.portfolio.domain.Valuation;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * The declared PEA valued on the latest known closes, in euros, rounded half-even to the cent.
 *
 * @param lines the lines, by instrument name
 * @param cash declared cash; absent until declared
 * @param total value of the priced lines plus the cash, decimal string at scale 2
 * @param currency always {@code EUR}
 * @param complete {@code false} when lines without any known close are left out of the total
 * @param unpricedLines number of lines left out of the total ("price unavailable")
 */
public record PortfolioResponse(
    List<PortfolioLineResponse> lines,
    @Nullable CashResponse cash,
    @Schema(example = "12500.40", pattern = "^\\d+\\.\\d{2}$") String total,
    String currency,
    boolean complete,
    int unpricedLines) {

  /** Defensive copy. */
  public PortfolioResponse {
    lines = List.copyOf(lines);
  }

  static PortfolioResponse from(PortfolioOverview overview) {
    Valuation valuation = overview.valuation();
    Cash cash = valuation.cash();
    return new PortfolioResponse(
        valuation.lines().stream()
            .map(line -> PortfolioLineResponse.from(line, overview.instrumentOf(line.line())))
            .toList(),
        cash == null ? null : CashResponse.from(cash, valuation.cashWeightPercent()),
        InputFormats.format(valuation.total()),
        Amounts.EUR.getCurrencyCode(),
        valuation.complete(),
        valuation.unpricedLines());
  }
}
