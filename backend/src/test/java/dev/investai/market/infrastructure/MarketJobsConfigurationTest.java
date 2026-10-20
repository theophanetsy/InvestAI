package dev.investai.market.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import dev.investai.market.application.DailyCollection;
import dev.investai.market.application.EodhdMaintenance;
import dev.investai.market.application.IndicatorComputation;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.SimpleAsyncTaskScheduler;
import org.springframework.scheduling.config.CronTask;
import org.springframework.scheduling.config.FixedDelayTask;
import org.springframework.scheduling.config.ScheduledTask;
import org.springframework.scheduling.config.ScheduledTaskHolder;
import org.springframework.scheduling.config.Task;

/**
 * The schedules of {@code application.properties} are valid and registered (no
 * {@code @IntegrationTest} starts them: jobs are off there). Use cases are mocks: nothing is
 * called. Values are collected inside the runner and asserted outside (PMD does not see assertions
 * in a lambda).
 */
class MarketJobsConfigurationTest {

  private final ApplicationContextRunner runner =
      new ApplicationContextRunner()
          .withInitializer(new ConfigDataApplicationContextInitializer())
          .withBean(DailyCollection.class, () -> mock(DailyCollection.class))
          .withBean(EodhdMaintenance.class, () -> mock(EodhdMaintenance.class))
          .withBean(IndicatorComputation.class, () -> mock(IndicatorComputation.class))
          .withBean(TaskScheduler.class, SimpleAsyncTaskScheduler::new)
          .withBean(Clock.class, Clock::systemUTC)
          .withUserConfiguration(MarketJobsConfiguration.class);

  @Test
  void jobsAreScheduledFromTheApplicationProperties() {
    List<Task> tasks = new ArrayList<>();
    AtomicInteger jobs = new AtomicInteger();

    runner.run(
        context -> {
          jobs.set(context.getBeansOfType(MarketJobs.class).size());
          context.getBean(ScheduledTaskHolder.class).getScheduledTasks().stream()
              .map(ScheduledTask::getTask)
              .forEach(tasks::add);
        });

    assertThat(jobs).hasValue(1);
    assertThat(tasks)
        .filteredOn(CronTask.class::isInstance)
        .map(task -> ((CronTask) task).getExpression())
        .containsExactly("0 0 9-23 * * *");
    assertThat(tasks)
        .filteredOn(task -> task instanceof FixedDelayTask && !(task instanceof CronTask))
        .map(FixedDelayTask.class::cast)
        .singleElement()
        .satisfies(
            delay -> {
              assertThat(delay.getIntervalDuration()).isEqualTo(Duration.ofHours(1));
              assertThat(delay.getInitialDelayDuration()).isEqualTo(Duration.ofSeconds(30));
            });
  }

  @Test
  void jobsCanBeSwitchedOff() {
    AtomicInteger jobs = new AtomicInteger(-1);

    runner
        .withPropertyValues("investai.market.jobs.enabled=false")
        .run(context -> jobs.set(context.getBeansOfType(MarketJobs.class).size()));

    assertThat(jobs).hasValue(0);
  }
}
