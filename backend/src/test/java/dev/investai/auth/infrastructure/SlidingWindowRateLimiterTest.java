package dev.investai.auth.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class SlidingWindowRateLimiterTest {

  private static final Instant START = Instant.parse("2026-10-20T10:00:00Z");
  private static final Duration MINUTE = Duration.ofMinutes(1);

  private final AtomicReference<Instant> now = new AtomicReference<>(START);
  private final SlidingWindowRateLimiter limiter =
      new SlidingWindowRateLimiter(3, MINUTE, new SettableClock(now));

  @Test
  void acceptsUpToTheLimitWithinTheWindow() {
    assertThat(limiter.tryAcquire().allowed()).isTrue();
    now.set(START.plusSeconds(10));
    assertThat(limiter.tryAcquire().allowed()).isTrue();
    now.set(START.plusSeconds(20));
    assertThat(limiter.tryAcquire().allowed()).isTrue();

    var refused = limiter.tryAcquire();

    assertThat(refused.allowed()).isFalse();
    // The first accepted request leaves the window 60 s after it, i.e. 40 s from now.
    assertThat(refused.retryAfter()).isEqualTo(Duration.ofSeconds(40));
    assertThat(refused.retryAfterSeconds()).isEqualTo(40);
  }

  @Test
  void theWindowSlidesInsteadOfResettingOnTheMinute() {
    limiter.tryAcquire();
    now.set(START.plusSeconds(59));
    limiter.tryAcquire();
    limiter.tryAcquire();
    assertThat(limiter.tryAcquire().allowed()).isFalse();

    now.set(START.plusSeconds(60));

    // Only the first request has left the window: one slot, not three.
    assertThat(limiter.tryAcquire().allowed()).isTrue();
    assertThat(limiter.tryAcquire().allowed()).isFalse();
  }

  @Test
  void refusedRequestsDoNotConsumeTheBudget() {
    for (int i = 0; i < 10; i++) {
      limiter.tryAcquire();
    }

    now.set(START.plus(MINUTE));

    assertThat(limiter.tryAcquire().allowed()).isTrue();
    assertThat(limiter.tryAcquire().allowed()).isTrue();
    assertThat(limiter.tryAcquire().allowed()).isTrue();
  }

  @Test
  void aBudgetBelowOneIsRefusedAtStartup() {
    assertThatThrownBy(() -> new SlidingWindowRateLimiter(0, MINUTE, new SettableClock(now)))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void retryAfterIsRoundedUpToAWholeSecond() {
    limiter.tryAcquire();
    limiter.tryAcquire();
    limiter.tryAcquire();
    now.set(START.plusMillis(59_500));

    assertThat(limiter.tryAcquire().retryAfterSeconds()).isEqualTo(1);
  }

  /** A clock reading the time from a reference the test moves. */
  private static final class SettableClock extends Clock {

    private final AtomicReference<Instant> time;

    SettableClock(AtomicReference<Instant> time) {
      this.time = time;
    }

    @Override
    public ZoneId getZone() {
      return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
      return this;
    }

    @Override
    public Instant instant() {
      return time.get();
    }
  }
}
