package dev.investai.market.infrastructure;

import dev.investai.market.application.MarketSettings;
import dev.investai.market.domain.Isin;
import dev.investai.market.domain.Listing;
import dev.investai.market.domain.MarketDataProvider;
import dev.investai.market.domain.Mic;
import dev.investai.market.domain.TradeFileSource;
import java.net.http.HttpClient;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * Wires the market data adapters (ADR-0009): Euronext files always, EODHD only when its key is set
 * (spec 2.1, criterion 15).
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(MarketProperties.class)
public class MarketConfiguration {

  /** Logged once at startup when the key is missing. */
  static final String EODHD_DISABLED =
      "INVESTAI_EODHD_API_KEY is not set: EODHD reference data, history and catch-up are disabled";

  private static final Logger LOG = LoggerFactory.getLogger(MarketConfiguration.class);
  private static final String BENCHMARK_SEPARATOR = ":";
  private static final String HTTPS = "https";

  /**
   * Settings of the market use cases.
   *
   * @param properties market properties
   * @return the settings
   */
  @Bean
  MarketSettings marketSettings(MarketProperties properties) {
    return new MarketSettings(
        properties.eodhd().dailyCallBudget(),
        properties.eodhd().referenceMaxAge(),
        benchmarks(properties.benchmarks()));
  }

  /**
   * HTTP client of the market adapters, closed by Spring at shutdown. No redirect: a redirect could
   * replay the EODHD key towards another host.
   *
   * @param properties market properties
   * @return the client
   */
  @Bean
  HttpClient marketHttpClient(MarketProperties properties) {
    return HttpClient.newBuilder()
        .connectTimeout(properties.http().connectTimeout())
        .followRedirects(HttpClient.Redirect.NEVER)
        .build();
  }

  /**
   * Euronext deferred trade files of Paris.
   *
   * @param properties market properties
   * @param marketHttpClient HTTP client
   * @return the source
   */
  @Bean
  TradeFileSource euronextTradeFiles(MarketProperties properties, HttpClient marketHttpClient) {
    return EuronextTradeFiles.create(
        restClient(marketHttpClient, properties),
        properties.euronext().baseUrl(),
        properties.userAgent(),
        new MifirFileReader(properties.euronext().maxUncompressedSize().toBytes()),
        properties.euronext().downloadTimeout());
  }

  /**
   * EODHD, or a disabled stand-in without key (one warning, the application still starts).
   *
   * @param properties market properties
   * @param marketHttpClient HTTP client
   * @return the provider
   */
  @Bean
  MarketDataProvider eodhd(MarketProperties properties, HttpClient marketHttpClient) {
    String key = properties.eodhd().apiKey();
    if (key == null || key.isBlank()) {
      LOG.warn(EODHD_DISABLED);
      return new DisabledMarketDataProvider();
    }
    if (!HTTPS.equals(properties.eodhd().baseUrl().getScheme())) {
      // The key travels in the query string: never over plain HTTP. No value in the message.
      throw new IllegalStateException("investai.market.eodhd.base-url must use https");
    }
    return EodhdClient.create(
        restClient(marketHttpClient, properties),
        properties.eodhd().baseUrl(),
        key.strip(),
        properties.userAgent());
  }

  private static RestClient.Builder restClient(HttpClient client, MarketProperties properties) {
    JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(client);
    factory.setReadTimeout(properties.http().readTimeout());
    return RestClient.builder().requestFactory(factory);
  }

  /** {@code ISIN:MIC} entries; an invalid one stops the startup (not a secret, safe to print). */
  static List<Listing> benchmarks(List<String> entries) {
    return entries.stream()
        .map(String::strip)
        .filter(entry -> !entry.isEmpty())
        .map(MarketConfiguration::benchmark)
        .toList();
  }

  private static Listing benchmark(String entry) {
    String[] parts = entry.split(BENCHMARK_SEPARATOR, -1);
    if (parts.length != 2 || !Isin.isValid(parts[0])) {
      throw new IllegalStateException(
          "investai.market.benchmarks: expected ISIN:MIC, got '" + entry + "'");
    }
    return new Listing(new Isin(parts[0]), new Mic(parts[1]));
  }
}
