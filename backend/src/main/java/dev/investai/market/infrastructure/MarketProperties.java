package dev.investai.market.infrastructure;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;
import org.springframework.validation.annotation.Validated;

/**
 * Market data settings ({@code investai.market.*}, defaults in {@code application.properties}).
 * Jobs are configured separately ({@code investai.market.jobs.*}, see {@code MarketJobs}).
 *
 * @param userAgent identifiable user agent of outgoing calls
 * @param http timeouts of outgoing calls
 * @param euronext deferred trade files
 * @param eodhd EODHD free plan
 * @param benchmarks listings tracked automatically, {@code ISIN:MIC}
 */
@Validated
@ConfigurationProperties("investai.market")
public record MarketProperties(
    @NotBlank String userAgent,
    @Valid @NotNull Http http,
    @Valid @NotNull Euronext euronext,
    @Valid @NotNull Eodhd eodhd,
    List<String> benchmarks) {

  /** Defensive copy. */
  public MarketProperties {
    benchmarks = List.copyOf(benchmarks);
  }

  /**
   * Timeouts of outgoing HTTP calls.
   *
   * @param connectTimeout connection timeout
   * @param readTimeout timeout between two reads of a response
   */
  public record Http(@NotNull Duration connectTimeout, @NotNull Duration readTimeout) {}

  /**
   * Euronext deferred trade files.
   *
   * @param baseUrl e.g. {@code https://marketdata.euronext.com}
   * @param maxUncompressedSize inflated size above which a file is rejected
   * @param downloadTimeout maximum duration of a whole download
   */
  public record Euronext(
      @NotNull URI baseUrl,
      @NotNull DataSize maxUncompressedSize,
      @NotNull Duration downloadTimeout) {}

  /**
   * EODHD free plan.
   *
   * @param baseUrl e.g. {@code https://eodhd.com/api}
   * @param apiKey {@code INVESTAI_EODHD_API_KEY}; blank disables EODHD features. No constraint on
   *     purpose: a binding error would print the value
   * @param dailyCallBudget calls per UTC day, kept below the free quota of 20
   * @param referenceMaxAge age after which the reference data is downloaded again
   */
  public record Eodhd(
      @NotNull URI baseUrl,
      @Nullable String apiKey,
      @Min(1) @Max(20) int dailyCallBudget,
      @NotNull Duration referenceMaxAge) {

    @Override
    public String toString() {
      return "Eodhd[baseUrl="
          + baseUrl
          + ", apiKey=<redacted>, dailyCallBudget="
          + dailyCallBudget
          + ", referenceMaxAge="
          + referenceMaxAge
          + "]";
    }
  }
}
