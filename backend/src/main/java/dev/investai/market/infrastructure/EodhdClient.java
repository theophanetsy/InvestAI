package dev.investai.market.infrastructure;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import dev.investai.market.domain.HistoricalClose;
import dev.investai.market.domain.Instrument;
import dev.investai.market.domain.MarketDataProvider;
import dev.investai.market.domain.MarketDataUnavailableException;
import dev.investai.market.domain.ReferenceData;
import java.math.BigDecimal;
import java.net.URI;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

/**
 * EODHD free plan (ADR-0009): reference data of Paris and raw daily closes. The key is added by
 * {@link ApiTokenInterceptor}; failures go through {@link HttpCalls}, which never keeps a URL.
 */
final class EodhdClient implements MarketDataProvider {

  private static final String PROVIDER = "EODHD";
  private static final ParameterizedTypeReference<List<EodhdSymbols.Symbol>> SYMBOLS =
      new ParameterizedTypeReference<>() {};
  private static final ParameterizedTypeReference<List<Bar>> BARS =
      new ParameterizedTypeReference<>() {};

  private final RestClient rest;

  private EodhdClient(RestClient rest) {
    this.rest = rest;
  }

  /**
   * Creates the client.
   *
   * @param builder a fresh builder (bound to a mock server in tests)
   * @param baseUrl API root, e.g. {@code https://eodhd.com/api}
   * @param apiKey the key, never logged
   * @param userAgent identifiable user agent (spec 2.1, criterion 14)
   * @return the client
   */
  static EodhdClient create(
      RestClient.Builder builder, URI baseUrl, String apiKey, String userAgent) {
    return new EodhdClient(
        builder
            .baseUrl(baseUrl)
            .defaultHeader(HttpHeaders.USER_AGENT, userAgent)
            .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
            .requestInterceptor(new ApiTokenInterceptor(apiKey))
            .build());
  }

  @Override
  public boolean isEnabled() {
    return true;
  }

  @Override
  public ReferenceData referenceData() {
    List<EodhdSymbols.Symbol> symbols =
        HttpCalls.call(
            PROVIDER,
            "exchange-symbol-list",
            () ->
                rest.get()
                    .uri("/exchange-symbol-list/{exchange}?fmt=json", EodhdSymbols.PARIS)
                    .retrieve()
                    .body(SYMBOLS));
    return EodhdSymbols.toReferenceData(symbols);
  }

  @Override
  public List<HistoricalClose> dailyCloses(Instrument instrument, LocalDate from, LocalDate to) {
    String symbol =
        EodhdSymbols.symbolOf(instrument.ticker(), instrument.listing().mic())
            .orElseThrow(() -> new MarketDataUnavailableException("EODHD eod: unsupported venue"));
    List<Bar> bars =
        HttpCalls.call(
            PROVIDER,
            "eod",
            () ->
                rest.get()
                    .uri("/eod/{symbol}?from={from}&to={to}&period=d&fmt=json", symbol, from, to)
                    .retrieve()
                    .body(BARS));
    return bars.stream()
        .filter(bar -> bar.date() != null && bar.close() != null)
        .map(
            bar ->
                new HistoricalClose(
                    Objects.requireNonNull(bar.date()),
                    Objects.requireNonNull(bar.close()),
                    decimal(bar.volume())))
        .toList();
  }

  /** A volume that is not a number is unknown: it never fails the call (spec 2.2, criterion 7). */
  private static @Nullable BigDecimal decimal(@Nullable String text) {
    if (text == null) {
      return null;
    }
    try {
      return new BigDecimal(text);
    } catch (NumberFormatException ex) {
      return null;
    }
  }

  /**
   * One daily bar of {@code eod}. Only the raw {@code close} is read: {@code adjusted_close}
   * includes dividends and splits, adjustments are computed by the application (ADR-0009).
   *
   * @param date the session
   * @param close the raw close, read as decimal text (never through a double)
   * @param volume units traded, read as text so that an unexpected value never fails the call
   */
  @JsonIgnoreProperties(ignoreUnknown = true)
  record Bar(@Nullable LocalDate date, @Nullable BigDecimal close, @Nullable String volume) {}
}
