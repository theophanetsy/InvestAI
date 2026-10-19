package dev.investai.market.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import dev.investai.market.application.DailyCollection;
import dev.investai.market.application.EodhdMaintenance;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.ScheduledFuture;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.springframework.scheduling.TaskScheduler;

class MarketJobsTest {

  private static final Instant NOW = Instant.parse("2026-10-19T20:30:00Z");
  private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

  private final DailyCollection collection = mock(DailyCollection.class);
  private final EodhdMaintenance maintenance = mock(EodhdMaintenance.class);
  private final TaskScheduler scheduler = mock(TaskScheduler.class);
  private final MarketJobs jobs =
      new MarketJobsConfiguration().marketJobs(collection, maintenance, scheduler, CLOCK, "PT1M");

  @Test
  void successfulCollectionIsFollowedByTheEodhdCatchUp() {
    given(collection.collect()).willReturn(DailyCollection.Outcome.COLLECTED);

    jobs.collectDailyCloses();

    verify(maintenance).run();
  }

  @ParameterizedTest
  @EnumSource(
      value = DailyCollection.Outcome.class,
      names = "COLLECTED",
      mode = EnumSource.Mode.EXCLUDE)
  void noCatchUpWithoutNewCollection(DailyCollection.Outcome outcome) {
    given(collection.collect()).willReturn(outcome);

    jobs.collectDailyCloses();

    verify(maintenance, never()).run();
  }

  @Test
  void startupSchedulesTheSameCollectionShortlyAfterwards() {
    ArgumentCaptor<Runnable> task = ArgumentCaptor.forClass(Runnable.class);
    given(collection.collect()).willReturn(DailyCollection.Outcome.COLLECTED);
    ScheduledFuture<?> pending = mock(ScheduledFuture.class);
    // Only matches one minute after now: the returned run proves the delay.
    given(scheduler.schedule(task.capture(), eq(NOW.plus(Duration.ofMinutes(1)))))
        .willAnswer(invocation -> pending);

    jobs.collectShortlyAfterStartup();

    assertThat(jobs.startupRun()).containsSame(pending);
    verify(collection, never()).collect();
    task.getValue().run();
    verify(collection).collect();
    verify(maintenance).run();
  }

  @Test
  void maintenanceJobRunsTheMaintenance() {
    jobs.maintainEodhdData();

    verify(maintenance).run();
  }
}
