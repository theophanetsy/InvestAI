package dev.investai.market.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;

import dev.investai.market.domain.ApiCallCounter;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.OptionalInt;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

@ExtendWith(OutputCaptureExtension.class)
class CallBudgetTest {

  private static final LocalDate UTC_DAY = LocalDate.parse("2026-10-18");

  private final ApiCallCounter counter = mock(ApiCallCounter.class);
  // 00:30 in Paris on the 19th is still the 18th in UTC: the quota follows UTC days.
  private final Clock clock =
      Clock.fixed(Instant.parse("2026-10-18T22:30:00Z"), ZoneId.of("Europe/Paris"));
  private final CallBudget budget =
      new CallBudget(counter, new MarketSettings(15, Duration.ofDays(7), List.of()), clock);

  @Test
  void reservedCallIsLoggedWithTheConsumption(CapturedOutput output) {
    given(counter.reserve(UTC_DAY, 15)).willReturn(OptionalInt.of(3));

    assertThat(budget.tryReserve("eod history")).isTrue();
    assertThat(output.getAll()).contains("operation=eod history, calls=3/15");
  }

  @Test
  void exhaustedBudgetRefusesTheCall(CapturedOutput output) {
    given(counter.reserve(UTC_DAY, 15)).willReturn(OptionalInt.empty());

    assertThat(budget.tryReserve("eod catch-up")).isFalse();
    assertThat(output.getAll()).contains("budget exhausted");
    then(counter).should().reserve(UTC_DAY, 15);
  }
}
