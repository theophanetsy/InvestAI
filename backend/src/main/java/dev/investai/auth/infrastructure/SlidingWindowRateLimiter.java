package dev.investai.auth.infrastructure;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.locks.ReentrantLock;

/**
 * At most {@code limit} accepted requests within any sliding window (rate limit of {@code /mcp},
 * spec 3.0, criterion 10). In memory: a single instance serves a single user. Refused requests do
 * not count.
 */
final class SlidingWindowRateLimiter {

  private final int limit;
  private final Duration window;
  private final Clock clock;
  private final Deque<Instant> accepted = new ArrayDeque<>();
  private final ReentrantLock lock = new ReentrantLock();

  /**
   * Creates the limiter.
   *
   * @param limit accepted requests per window, at least 1
   * @param window length of the window
   * @param clock current time
   * @throws IllegalArgumentException when the limit is below 1 (fails at startup)
   */
  SlidingWindowRateLimiter(int limit, Duration window, Clock clock) {
    if (limit < 1) {
      throw new IllegalArgumentException("investai.mcp.requests-per-minute must be at least 1");
    }
    this.limit = limit;
    this.window = window;
    this.clock = clock;
  }

  /**
   * Accepts a request if the budget allows it.
   *
   * @return the decision, with the wait before a slot frees up when refused
   */
  Decision tryAcquire() {
    lock.lock();
    try {
      return decide(clock.instant());
    } finally {
      lock.unlock();
    }
  }

  private Decision decide(Instant now) {
    Instant windowStart = now.minus(window);
    while (!accepted.isEmpty() && !accepted.peekFirst().isAfter(windowStart)) {
      accepted.removeFirst();
    }
    if (accepted.size() < limit) {
      accepted.addLast(now);
      return new Decision(true, Duration.ZERO);
    }
    return new Decision(false, Duration.between(windowStart, accepted.peekFirst()));
  }

  /**
   * Outcome of {@link #tryAcquire()}.
   *
   * @param allowed whether the request is accepted
   * @param retryAfter wait before a slot frees up, zero when accepted
   */
  record Decision(boolean allowed, Duration retryAfter) {

    /**
     * Wait in whole seconds, rounded up, for the {@code Retry-After} header.
     *
     * @return the seconds, at least 1 when refused
     */
    long retryAfterSeconds() {
      long seconds = retryAfter.toSeconds();
      boolean whole = retryAfter.equals(Duration.ofSeconds(seconds));
      return Math.max(1, whole ? seconds : seconds + 1);
    }
  }
}
