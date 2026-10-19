package dev.investai.market.application;

import dev.investai.market.application.PriceHistoryLoader.Step;
import dev.investai.market.domain.MarketDataProvider;
import java.util.concurrent.locks.ReentrantLock;
import org.springframework.stereotype.Service;

/**
 * EODHD job: spends the daily budget in priority order (spec 2.1, criterion 16) — catch-up of
 * missed sessions, then history of newly tracked instruments, then reference data. After a
 * reference refresh, benchmarks are tracked and their history loaded in the same run.
 */
@Service
public class EodhdMaintenance {

  private final ReentrantLock running = new ReentrantLock();
  private final MarketDataProvider provider;
  private final InstrumentTracking tracking;
  private final PriceHistoryLoader history;
  private final ReferenceDataLoader reference;

  /**
   * Creates the job.
   *
   * @param provider EODHD, possibly disabled
   * @param tracking benchmarks
   * @param history catch-up and history
   * @param reference reference data
   */
  public EodhdMaintenance(
      MarketDataProvider provider,
      InstrumentTracking tracking,
      PriceHistoryLoader history,
      ReferenceDataLoader reference) {
    this.provider = provider;
    this.tracking = tracking;
    this.history = history;
    this.reference = reference;
  }

  /** Runs the job; does nothing when EODHD is disabled or another run is in progress. */
  public void run() {
    if (!provider.isEnabled() || !running.tryLock()) {
      return;
    }
    try {
      tracking.trackBenchmarks();
      if (history.catchUp() == Step.STOPPED || history.loadMissingHistories() == Step.STOPPED) {
        return;
      }
      if (reference.refreshIfDue()) {
        tracking.trackBenchmarks();
        history.loadMissingHistories();
      }
    } finally {
      running.unlock();
    }
  }
}
