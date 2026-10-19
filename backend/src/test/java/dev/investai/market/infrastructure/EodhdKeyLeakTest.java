package dev.investai.market.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import dev.investai.market.application.CallBudget;
import dev.investai.market.application.MarketSettings;
import dev.investai.market.application.PriceHistoryLoader;
import dev.investai.market.application.PriceRecorder;
import dev.investai.market.application.ReferenceDataLoader;
import dev.investai.market.domain.ApiCallCounter;
import dev.investai.market.domain.CatchUpQueue;
import dev.investai.market.domain.Instrument;
import dev.investai.market.domain.InstrumentId;
import dev.investai.market.domain.InstrumentRepository;
import dev.investai.market.domain.InstrumentType;
import dev.investai.market.domain.Isin;
import dev.investai.market.domain.Listing;
import dev.investai.market.domain.Mic;
import java.io.IOException;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Currency;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/**
 * Spec 2.1, criterion 15: a network error, an HTTP error and an unreadable response go through the
 * real EODHD client and the use cases that log them; the key never shows up in the captured logs.
 */
@ExtendWith(OutputCaptureExtension.class)
class EodhdKeyLeakTest {

  private static final URI BASE_URL = URI.create("https://eodhd.test/api");

  private final String key = "k" + UUID.randomUUID().toString().replace("-", "");
  private final RestClient.Builder builder = RestClient.builder();
  private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
  private final EodhdClient client = EodhdClient.create(builder, BASE_URL, key, "InvestAI/test");
  private final Clock clock = Clock.fixed(Instant.parse("2026-10-19T08:00:00Z"), ZoneOffset.UTC);
  private final InstrumentRepository instruments = mock(InstrumentRepository.class);
  private final ApiCallCounter counter = mock(ApiCallCounter.class);
  private final MarketSettings settings = new MarketSettings(15, Duration.ofDays(7), List.of());
  private final CallBudget budget = new CallBudget(counter, settings, clock);

  @Test
  void failuresAreLoggedWithoutTheKey(CapturedOutput output) throws IOException {
    Instrument instrument =
        new Instrument(
            InstrumentId.newId(),
            new Listing(new Isin("FR0000120073"), Mic.XPAR),
            "AI",
            "Air Liquide",
            InstrumentType.STOCK,
            Currency.getInstance("EUR"),
            Instant.EPOCH,
            false);
    given(counter.reserve(any(), anyInt())).willReturn(OptionalInt.of(1));
    given(instruments.findTracked()).willReturn(List.of(instrument));
    given(instruments.lastReferenceRefresh()).willReturn(Optional.empty());
    server
        .expect(ExpectedCount.once(), request -> {})
        .andRespond(
            withException(
                new IOException(
                    "reset by " + BASE_URL + "?" + String.join("=", "api_token", key))));
    server
        .expect(ExpectedCount.once(), request -> {})
        .andRespond(
            withStatus(HttpStatus.FORBIDDEN).body(String.join("=", "api_token", key) + " refused"));
    server
        .expect(ExpectedCount.once(), request -> {})
        .andRespond(withSuccess("{not json " + key, MediaType.APPLICATION_JSON));
    var reference = new ReferenceDataLoader(client, budget, instruments, settings, clock);

    // A failed instrument waits for the next day: a fresh loader for each failing call.
    historyLoader().loadMissingHistories();
    reference.refreshIfDue();
    historyLoader().loadMissingHistories();

    server.verify();
    assertThat(output.getAll())
        .contains("EODHD eod failed: ResourceAccessException")
        .contains("EODHD exchange-symbol-list failed: HTTP 403")
        .contains("EODHD call failed")
        .doesNotContain(key)
        .doesNotContain("api_token");
  }

  private PriceHistoryLoader historyLoader() {
    return new PriceHistoryLoader(
        client, budget, instruments, mock(CatchUpQueue.class), mock(PriceRecorder.class), clock);
  }
}
