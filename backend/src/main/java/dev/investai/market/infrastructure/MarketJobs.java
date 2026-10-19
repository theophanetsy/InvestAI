package dev.investai.market.infrastructure;

import dev.investai.market.application.DailyCollection;
import dev.investai.market.application.EodhdMaintenance;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * The only callers of the market data providers (ADR-0009). Disabled with {@code
 * investai.market.jobs.enabled=false} (tests, verification stack): use cases are then triggered
 * explicitly. A single instance runs: no ShedLock before the multi-instance deployment (phase 10),
 * idempotence relies on unique constraints.
 */
public class MarketJobs {

  private final DailyCollection collection;
  private final EodhdMaintenance maintenance;

  /**
   * Creates the jobs.
   *
   * @param collection Euronext collection
   * @param maintenance EODHD maintenance
   */
  public MarketJobs(DailyCollection collection, EodhdMaintenance maintenance) {
    this.collection = collection;
    this.maintenance = maintenance;
  }

  /**
   * Hourly from 9:00 to 18:00, Paris time, every day: collects the previous session, then catches
   * up missed sessions with EODHD.
   */
  @Scheduled(cron = "${investai.market.jobs.collection-cron}", zone = "Europe/Paris")
  public void collectDailyCloses() {
    if (collection.collect() == DailyCollection.Outcome.COLLECTED) {
      maintenance.run();
    }
  }

  /** At startup, then hourly: catch-up, history of tracked instruments, reference data. */
  @Scheduled(
      fixedDelayString = "${investai.market.jobs.maintenance-interval}",
      initialDelayString = "${investai.market.jobs.maintenance-initial-delay}")
  public void maintainEodhdData() {
    maintenance.run();
  }
}
