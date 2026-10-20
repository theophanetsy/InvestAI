package dev.investai.assistant.infrastructure;

import java.math.BigDecimal;
import java.util.Currency;

/**
 * An amount sent to the model (spec 3.1, criterion 3): a decimal string, never a binary float, and
 * its currency.
 *
 * @param amount plain decimal string, e.g. {@code "20000.00"}
 * @param currency ISO 4217 code, e.g. {@code EUR}
 */
public record Money(String amount, String currency) {

  static Money of(BigDecimal amount, Currency currency) {
    return new Money(amount.toPlainString(), currency.getCurrencyCode());
  }
}
