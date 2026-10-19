package dev.investai.market.application;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import org.jspecify.annotations.Nullable;

/** A clock the test moves forward. */
final class MutableClock extends Clock {
  private Instant now;

  MutableClock(Instant now) {
    this.now = now;
  }

  void set(Instant instant) {
    now = instant;
  }

  @Override
  public ZoneId getZone() {
    return ZoneId.of("UTC");
  }

  @Override
  public Clock withZone(@Nullable ZoneId zone) {
    return zone == null ? this : fixed(now, zone);
  }

  @Override
  public Instant instant() {
    return now;
  }
}
