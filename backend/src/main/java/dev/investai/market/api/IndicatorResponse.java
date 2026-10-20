package dev.investai.market.api;

import dev.investai.market.domain.Indicator;
import dev.investai.market.domain.IndicatorStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import org.jspecify.annotations.Nullable;

/**
 * A computed figure, or why it is missing. Shared with the portfolio API (one type in the generated
 * client).
 *
 * @param status {@code AVAILABLE}; {@code INSUFFICIENT_HISTORY} when fewer than 80 % of the
 *     sessions of the window have a close; {@code NOT_COMPUTABLE} when undefined
 * @param value decimal string, present exactly when available; never an approximate value
 */
@Schema(
    description =
        "Indicateur calculé par l'application, ou la raison de son absence : jamais de valeur"
            + " approchée (historique insuffisant = moins de 80 % des séances de la fenêtre).")
public record IndicatorResponse(
    IndicatorStatus status, @Schema(example = "12.34") @Nullable String value) {

  /**
   * Converts the domain value.
   *
   * @param indicator the indicator
   * @return the response
   */
  public static IndicatorResponse from(Indicator indicator) {
    BigDecimal value = indicator.value();
    return new IndicatorResponse(indicator.status(), value == null ? null : value.toPlainString());
  }
}
