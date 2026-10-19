package dev.investai.market.infrastructure;

import dev.investai.market.application.DailyCollection;
import dev.investai.market.application.EodhdMaintenance;
import java.time.Clock;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Registers {@link MarketJobs} unless {@code investai.market.jobs.enabled=false}. */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
@ConditionalOnBooleanProperty(name = "investai.market.jobs.enabled", matchIfMissing = true)
public class MarketJobsConfiguration {

  /**
   * The scheduled jobs.
   *
   * @param collection Euronext collection
   * @param maintenance EODHD maintenance
   * @param scheduler the application task scheduler
   * @param clock current time
   * @param startupDelay delay before the collection run after startup, ISO-8601
   * @return the jobs
   */
  @Bean
  MarketJobs marketJobs(
      DailyCollection collection,
      EodhdMaintenance maintenance,
      TaskScheduler scheduler,
      Clock clock,
      @Value("${investai.market.jobs.collection-startup-delay}") String startupDelay) {
    // ISO-8601 (PT1M), parsed here: no dependency on the Boot conversion service.
    return new MarketJobs(collection, maintenance, scheduler, clock, Duration.parse(startupDelay));
  }
}
