package dev.investai.market.infrastructure;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import dev.investai.market.domain.InstrumentType;
import dev.investai.market.domain.Isin;
import dev.investai.market.domain.ListedInstrument;
import dev.investai.market.domain.Listing;
import dev.investai.market.domain.Mic;
import dev.investai.market.domain.ReferenceData;
import java.util.ArrayList;
import java.util.Currency;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/**
 * Rules turning the EODHD symbol list of Paris ({@code exchange-symbol-list/PA}) into reference
 * data: Euronext Growth codes start with {@code AL} ({@code ALXP}), everything else is the
 * regulated market ({@code XPAR}). The EODHD symbol ({@code AI.PA}) stays an adapter detail
 * (ADR-0009).
 */
final class EodhdSymbols {

  /** EODHD exchange code of Euronext Paris. */
  static final String PARIS = "PA";

  private static final String GROWTH_PREFIX = "AL";
  private static final int MAX_TICKER_LENGTH = 32;
  private static final int MAX_NAME_LENGTH = 256;

  private EodhdSymbols() {}

  /**
   * Maps the symbol list; entries without a valid ISIN, code, name or currency are ignored.
   *
   * @param symbols the list returned by EODHD
   * @return usable entries (first one kept per listing) and the number of ignored ones
   */
  static ReferenceData toReferenceData(List<Symbol> symbols) {
    List<ListedInstrument> instruments = new ArrayList<>();
    Set<Listing> listings = new HashSet<>();
    for (Symbol symbol : symbols) {
      toInstrument(symbol)
          .filter(instrument -> listings.add(instrument.listing()))
          .ifPresent(instruments::add);
    }
    return new ReferenceData(instruments, symbols.size() - instruments.size());
  }

  /**
   * Returns the EODHD symbol of an instrument.
   *
   * @param ticker the exchange ticker
   * @param mic the venue
   * @return {@code TICKER.PA}, or empty for a venue other than Paris
   */
  static Optional<String> symbolOf(String ticker, Mic mic) {
    return Mic.XPAR.equals(mic) || Mic.ALXP.equals(mic)
        ? Optional.of(ticker + "." + PARIS)
        : Optional.empty();
  }

  private static Optional<ListedInstrument> toInstrument(Symbol symbol) {
    String code = symbol.code();
    String name = symbol.name();
    String isin = symbol.isin();
    if (code == null || name == null || isin == null) {
      return Optional.empty();
    }
    if (!isUsableCode(code) || name.isBlank() || !Isin.isValid(isin)) {
      return Optional.empty();
    }
    return currencyOf(symbol.currency())
        .map(
            currency ->
                new ListedInstrument(
                    new Listing(new Isin(isin), micOf(code)),
                    code,
                    truncate(name.strip()),
                    typeOf(symbol.type()),
                    currency));
  }

  private static boolean isUsableCode(String code) {
    return !code.isBlank() && code.length() <= MAX_TICKER_LENGTH;
  }

  private static Mic micOf(String code) {
    return code.toUpperCase(Locale.ROOT).startsWith(GROWTH_PREFIX) ? Mic.ALXP : Mic.XPAR;
  }

  private static InstrumentType typeOf(@Nullable String type) {
    return switch (String.valueOf(type)) {
      case "Common Stock" -> InstrumentType.STOCK;
      case "ETF" -> InstrumentType.ETF;
      case "FUND" -> InstrumentType.FUND;
      default -> InstrumentType.OTHER;
    };
  }

  private static Optional<Currency> currencyOf(@Nullable String code) {
    try {
      return Optional.of(Currency.getInstance(String.valueOf(code)));
    } catch (IllegalArgumentException ex) {
      return Optional.empty();
    }
  }

  private static String truncate(String name) {
    return name.length() > MAX_NAME_LENGTH ? name.substring(0, MAX_NAME_LENGTH) : name;
  }

  /**
   * One entry of {@code exchange-symbol-list} (only the fields used here).
   *
   * @param code exchange ticker, e.g. {@code AI}
   * @param name instrument name
   * @param type EODHD type, e.g. {@code Common Stock}, {@code ETF}, {@code FUND}
   * @param currency trading currency
   * @param isin ISIN, often missing
   */
  @JsonIgnoreProperties(ignoreUnknown = true)
  record Symbol(
      @JsonProperty("Code") @Nullable String code,
      @JsonProperty("Name") @Nullable String name,
      @JsonProperty("Type") @Nullable String type,
      @JsonProperty("Currency") @Nullable String currency,
      @JsonProperty("Isin") @Nullable String isin) {}
}
