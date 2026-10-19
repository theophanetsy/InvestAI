package dev.investai.market.infrastructure;

import dev.investai.market.application.DailyCollection;
import dev.investai.market.application.EodhdMaintenance;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
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
   * @return the jobs
   */
  @Bean
  MarketJobs marketJobs(DailyCollection collection, EodhdMaintenance maintenance) {
    return new MarketJobs(collection, maintenance);
  }
}
