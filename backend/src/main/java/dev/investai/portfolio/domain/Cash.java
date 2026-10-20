package dev.investai.portfolio.domain;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Cash available on the PEA, in euros (spec 2.3, criterion 4).
 *
 * @param amount non-negative, at most 2 decimal places (stored at scale 2)
 * @param updatedAt last change
 */
public record Cash(BigDecimal amount, Instant updatedAt) {

  /** Checks the amount. */
  public Cash {
    if (amount.signum() < 0) {
      throw new IllegalArgumentException("Negative cash");
    }
    amount = Amounts.exact(amount, Amounts.CENTS);
  }
}
