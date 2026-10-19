package dev.investai.portfolio.api;

import dev.investai.market.api.InstrumentSummaryResponse;
import dev.investai.market.domain.Instrument;
import dev.investai.portfolio.domain.Quote;
import dev.investai.portfolio.domain.ValuedLine;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * A valued line of the declared PEA. Amounts are decimal strings in euros. Without any known close
 * ("price unavailable"), {@code lastClose}, {@code value}, {@code weightPercent} and the gain are
 * absent: the line is never valued at zero.
 *
 * @param id identifier of the line
 * @param instrument the instrument, with its PEA eligibility (same type as the search results)
 * @param quantity number of units held
 * @param averagePrice average purchase price, scale 4; absent when not given
 * @param updatedAt last change of the declared values
 * @param lastClose latest known close and its session
 * @param value quantity × latest close, scale 2
 * @param unrealizedGain value − quantity × average price, scale 2, negative for a loss; absent
 *     without average price
 * @param unrealizedGainPercent latest close compared with the average price, in percent, scale 2
 * @param weightPercent share of the total value (cash included), in percent, scale 2
 */
public record PortfolioLineResponse(
    UUID id,
    InstrumentSummaryResponse instrument,
    long quantity,
    @Schema(example = "150.1234") @Nullable String averagePrice,
    Instant updatedAt,
    @Nullable LastCloseResponse lastClose,
    @Schema(example = "1731.60") @Nullable String value,
    @Schema(example = "230.37") @Nullable String unrealizedGain,
    @Schema(example = "15.35") @Nullable String unrealizedGainPercent,
    @Schema(example = "42.10") @Nullable String weightPercent) {

  static PortfolioLineResponse from(ValuedLine valued, Instrument instrument) {
    Quote quote = valued.quote();
    return new PortfolioLineResponse(
        valued.line().id().value(),
        InstrumentSummaryResponse.from(instrument),
        valued.line().quantity(),
        text(valued.line().averagePrice()),
        valued.line().updatedAt(),
        quote == null
            ? null
            : new LastCloseResponse(quote.date(), InputFormats.format(quote.close())),
        text(valued.value()),
        text(valued.unrealizedGain()),
        text(valued.unrealizedGainPercent()),
        text(valued.weightPercent()));
  }

  private static @Nullable String text(@Nullable BigDecimal value) {
    return value == null ? null : InputFormats.format(value);
  }

  /**
   * Latest known close used for the valuation.
   *
   * @param date the session
   * @param close the raw close in euros, decimal string at scale 4
   */
  public record LastCloseResponse(LocalDate date, @Schema(example = "173.1600") String close) {}
}
