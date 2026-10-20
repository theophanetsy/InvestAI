package dev.investai.assistant.infrastructure;

import com.fasterxml.jackson.annotation.JsonInclude;
import dev.investai.market.domain.Indicator;
import dev.investai.market.domain.IndicatorStatus;
import java.math.BigDecimal;
import org.jspecify.annotations.Nullable;

/**
 * An indicator sent to the model (spec 3.1, criterion 4): its status, and its value exactly when
 * available, never a zero in place of a missing figure.
 *
 * @param status {@code AVAILABLE}, {@code INSUFFICIENT_HISTORY} or {@code NOT_COMPUTABLE}
 * @param value plain decimal string, present exactly when available
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record IndicatorValue(IndicatorStatus status, @Nullable String value) {

  static IndicatorValue of(Indicator indicator) {
    BigDecimal value = indicator.value();
    return new IndicatorValue(indicator.status(), value == null ? null : value.toPlainString());
  }
}
