package dev.investai.market.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import dev.investai.market.domain.CollectionJournal;
import dev.investai.market.domain.DailyPriceRepository;
import dev.investai.market.domain.IndicatorRepository;
import dev.investai.market.domain.InstrumentRepository;
import dev.investai.market.domain.Isin;
import dev.investai.market.domain.Listing;
import dev.investai.market.domain.Mic;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

/** Concurrency of the daily computation (spec 2.4, criterion 6): the two jobs may overlap. */
class IndicatorComputationTest {

  private static final Listing BENCHMARK = new Listing(new Isin("LU1681043599"), Mic.XPAR);

  @Test
  void aRunStartedWhileAnotherIsInProgressDoesNothing() throws Exception {
    var prices = mock(DailyPriceRepository.class);
    var entered = new CountDownLatch(1);
    var release = new CountDownLatch(1);
    given(prices.latestSessionBefore(any()))
        .willAnswer(
            invocation -> {
              entered.countDown();
              release.await(5, TimeUnit.SECONDS);
              return Optional.empty();
            });
    var computation =
        new IndicatorComputation(
            mock(InstrumentRepository.class),
            prices,
            mock(CollectionJournal.class),
            mock(IndicatorRepository.class),
            new IndicatorSettings(BENCHMARK, BENCHMARK),
            Clock.fixed(Instant.parse("2026-10-19T08:00:00Z"), ZoneOffset.UTC));

    var first = CompletableFuture.supplyAsync(computation::refresh);
    assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
    var second = computation.refresh();
    release.countDown();

    assertThat(second).isEqualTo(IndicatorComputation.Outcome.BUSY);
    assertThat(first.get(5, TimeUnit.SECONDS)).isEqualTo(IndicatorComputation.Outcome.NO_CLOSES);
  }
}
