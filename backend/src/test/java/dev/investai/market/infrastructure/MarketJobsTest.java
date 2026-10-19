package dev.investai.market.infrastructure;

import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;

import dev.investai.market.application.DailyCollection;
import dev.investai.market.application.EodhdMaintenance;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class MarketJobsTest {

  private final DailyCollection collection = mock(DailyCollection.class);
  private final EodhdMaintenance maintenance = mock(EodhdMaintenance.class);
  private final MarketJobs jobs = new MarketJobsConfiguration().marketJobs(collection, maintenance);

  @Test
  void successfulCollectionIsFollowedByTheEodhdCatchUp() {
    given(collection.collect()).willReturn(DailyCollection.Outcome.COLLECTED);

    jobs.collectDailyCloses();

    then(maintenance).should().run();
  }

  @ParameterizedTest
  @EnumSource(
      value = DailyCollection.Outcome.class,
      names = "COLLECTED",
      mode = EnumSource.Mode.EXCLUDE)
  void noCatchUpWithoutNewCollection(DailyCollection.Outcome outcome) {
    given(collection.collect()).willReturn(outcome);

    jobs.collectDailyCloses();

    then(maintenance).should(never()).run();
  }

  @Test
  void maintenanceJobRunsTheMaintenance() {
    jobs.maintainEodhdData();

    then(maintenance).should().run();
  }
}
