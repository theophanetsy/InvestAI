package dev.investai.market.application;

import dev.investai.market.domain.ApiCallCounter;
import dev.investai.market.domain.CatchUpQueue;
import dev.investai.market.domain.InstrumentRepository;
import dev.investai.market.domain.Isin;
import dev.investai.market.domain.Listing;
import dev.investai.market.domain.MarketDataProvider;
import dev.investai.market.domain.Mic;
import java.time.Clock;
import java.time.Duration;
import java.util.List;

/** Builds an {@link EodhdMaintenance} on real adapters with a given provider, clock and budget. */
record MaintenanceFactory(
    InstrumentRepository instruments,
    CatchUpQueue queue,
    ApiCallCounter usage,
    PriceRecorder recorder) {

  EodhdMaintenance create(MarketDataProvider provider, Clock clock, int budget, Isin benchmark) {
    var settings =
        new MarketSettings(budget, Duration.ofDays(7), List.of(new Listing(benchmark, Mic.XPAR)));
    var callBudget = new CallBudget(usage, settings, clock);
    return new EodhdMaintenance(
        provider,
        new InstrumentTracking(instruments, settings, clock),
        new PriceHistoryLoader(provider, callBudget, instruments, queue, recorder, clock),
        new ReferenceDataLoader(provider, callBudget, instruments, settings, clock));
  }
}
