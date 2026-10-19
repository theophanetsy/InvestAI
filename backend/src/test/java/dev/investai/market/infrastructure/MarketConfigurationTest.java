package dev.investai.market.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import dev.investai.market.domain.Isin;
import dev.investai.market.domain.Listing;
import dev.investai.market.domain.Mic;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.util.unit.DataSize;

@ExtendWith(OutputCaptureExtension.class)
class MarketConfigurationTest {

  private final MarketConfiguration configuration = new MarketConfiguration();
  private final HttpClient client = configuration.marketHttpClient(properties(null));

  @Test
  void missingKeyDisablesEodhdWithASingleWarning(CapturedOutput output) {
    var provider = configuration.eodhd(properties(null), client);

    assertThat(provider.isEnabled()).isFalse();
    assertThat(output.getAll().split(MarketConfiguration.EODHD_DISABLED, -1)).hasSize(2);
  }

  @Test
  void blankKeyIsAMissingKey() {
    assertThat(configuration.eodhd(properties("  "), client).isEnabled()).isFalse();
  }

  @Test
  void keyEnablesEodhdAndIsNeverPrinted(CapturedOutput output) {
    String key = UUID.randomUUID().toString();
    MarketProperties properties = properties(key);

    assertThat(configuration.eodhd(properties, client).isEnabled()).isTrue();
    assertThat(properties.toString()).doesNotContain(key).contains("<redacted>");
    assertThat(output.getAll()).doesNotContain(key);
  }

  @Test
  void euronextSourceAndSettingsAreBuiltFromTheProperties() {
    MarketProperties properties = properties(null);

    assertThat(configuration.euronextTradeFiles(properties, client)).isNotNull();
    assertThat(configuration.marketSettings(properties).benchmarks())
        .containsExactly(new Listing(new Isin("FR0007052782"), Mic.XPAR));
    assertThat(configuration.marketSettings(properties).dailyCallBudget()).isEqualTo(15);
  }

  @Test
  void benchmarksAreIsinColonMic() {
    assertThat(
            MarketConfiguration.benchmarks(List.of(" LU1681043599:XPAR ", "", "FR0000033888:ALXP")))
        .containsExactly(
            new Listing(new Isin("LU1681043599"), Mic.XPAR),
            new Listing(new Isin("FR0000033888"), Mic.ALXP));
  }

  @ParameterizedTest
  @ValueSource(strings = {"FR0007052782", "FR0007052783:XPAR", "FR0007052782:XPAR:X"})
  void invalidBenchmarkStopsTheStartup(String entry) {
    assertThatIllegalStateException()
        .isThrownBy(() -> MarketConfiguration.benchmarks(List.of(entry)))
        .withMessageContaining("investai.market.benchmarks");
  }

  private static MarketProperties properties(@Nullable String key) {
    return new MarketProperties(
        "InvestAI/test",
        new MarketProperties.Http(Duration.ofSeconds(1), Duration.ofSeconds(1)),
        new MarketProperties.Euronext(URI.create("https://euronext.test"), DataSize.ofMegabytes(1)),
        new MarketProperties.Eodhd(
            URI.create("https://eodhd.test/api"), key, 15, Duration.ofDays(7)),
        List.of("FR0007052782:XPAR"));
  }
}
