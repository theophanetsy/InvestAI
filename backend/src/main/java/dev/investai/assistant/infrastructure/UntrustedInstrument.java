package dev.investai.assistant.infrastructure;

import dev.investai.assistant.domain.UntrustedText;
import dev.investai.market.domain.Instrument;

/**
 * Third-party text about an instrument (EODHD, Euronext), always under {@code untrusted}: data for
 * the model, never instructions (spec 3.1, criterion 7). Cleaned and bounded.
 *
 * @param name the name, at most {@value #MAX_NAME} characters
 * @param ticker the ticker, at most {@value #MAX_TICKER} characters
 */
public record UntrustedInstrument(String name, String ticker) {

  /** Longest name sent. */
  static final int MAX_NAME = 100;

  /** Longest ticker sent. */
  static final int MAX_TICKER = 32;

  static UntrustedInstrument of(Instrument instrument) {
    return new UntrustedInstrument(
        UntrustedText.clean(instrument.name(), MAX_NAME),
        UntrustedText.clean(instrument.ticker(), MAX_TICKER));
  }
}
