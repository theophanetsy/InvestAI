package dev.investai.market.infrastructure;

import dev.investai.market.application.DailyCollection;
import dev.investai.market.application.EodhdMaintenance;
import dev.investai.market.application.IndicatorComputation;
import java.time.Clock;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicReference;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * The only callers of the market data providers (ADR-0009), followed by the daily computation of
 * the indicators (spec 2.4, criterion 6). Disabled with {@code investai.market.jobs.enabled=false}
 * (tests, verification stack): use cases are then triggered explicitly. A single instance runs: no
 * ShedLock before the multi-instance deployment (phase 9), idempotence relies on unique
 * constraints. The two jobs may still run at the same time (scheduler on virtual threads): each use
 * case has its own lock and their shared writes are safe.
 */
public class MarketJobs {

  private final DailyCollection collection;
  private final EodhdMaintenance maintenance;
  private final IndicatorComputation indicators;
  private final TaskScheduler scheduler;
  private final Clock clock;
  private final Duration startupDelay;

  /** Pending startup run; its failures are logged by the scheduler error handler. */
  private final AtomicReference<@Nullable ScheduledFuture<?>> startupRun = new AtomicReference<>();

  /**
   * Creates the jobs.
   *
   * @param collection Euronext collection
   * @param maintenance EODHD maintenance
   * @param indicators daily computation of the indicators
   * @param scheduler runs the collection shortly after startup
   * @param clock current time
   * @param startupDelay delay between the application start and the first collection
   */
  public MarketJobs(
      DailyCollection collection,
      EodhdMaintenance maintenance,
      IndicatorComputation indicators,
      TaskScheduler scheduler,
      Clock clock,
      Duration startupDelay) {
    this.collection = collection;
    this.maintenance = maintenance;
    this.indicators = indicators;
    this.scheduler = scheduler;
    this.clock = clock;
    this.startupDelay = startupDelay;
  }

  /**
   * Hourly from 9:00 to 23:00, Paris time, every day (the application often runs in the evening):
   * collects the previous session, then catches up missed sessions with EODHD, then computes the
   * indicators when closes changed. Nothing is downloaded when the session is already complete,
   * nothing is computed when the indicators are up to date.
   */
  @Scheduled(cron = "${investai.market.jobs.collection-cron}", zone = "Europe/Paris")
  public void collectDailyCloses() {
    if (collection.collect() == DailyCollection.Outcome.COLLECTED) {
      maintenance.run();
    }
    indicators.refresh();
  }

  /**
   * Runs the same collection shortly after startup, whatever the time: the application may be
   * started between two hourly runs, or outside the cron window.
   */
  @EventListener(ApplicationReadyEvent.class)
  public void collectShortlyAfterStartup() {
    startupRun.set(
        scheduler.schedule(this::collectDailyCloses, clock.instant().plus(startupDelay)));
  }

  /**
   * Returns the run scheduled after startup.
   *
   * @return the pending run, empty before the application is ready
   */
  Optional<ScheduledFuture<?>> startupRun() {
    return Optional.ofNullable(startupRun.get());
  }

  /**
   * At startup, then hourly: catch-up, history of tracked instruments, reference data; then the
   * indicators when closes changed (a history just loaded, a session caught up).
   */
  @Scheduled(
      fixedDelayString = "${investai.market.jobs.maintenance-interval}",
      initialDelayString = "${investai.market.jobs.maintenance-initial-delay}")
  public void maintainEodhdData() {
    maintenance.run();
    indicators.refresh();
  }
}
