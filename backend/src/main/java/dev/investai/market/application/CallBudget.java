package dev.investai.market.application;

import dev.investai.market.domain.ApiCallCounter;
import dev.investai.shared.logging.LogSanitizer;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.OptionalInt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Daily budget of EODHD calls (spec 2.1, criterion 16): a call is reserved in the database before
 * it is made, and the consumption is logged ({@code calls=3/15}). A refused call is postponed by
 * its caller, never lost.
 */
@Service
public class CallBudget {

  private static final Logger LOG = LoggerFactory.getLogger(CallBudget.class);

  private final ApiCallCounter counter;
  private final MarketSettings settings;
  private final Clock clock;

  /**
   * Creates the budget.
   *
   * @param counter calls already made per day
   * @param settings the daily limit
   * @param clock current time; days are UTC days, like the provider quota
   */
  public CallBudget(ApiCallCounter counter, MarketSettings settings, Clock clock) {
    this.counter = counter;
    this.settings = settings;
    this.clock = clock;
  }

  /**
   * Reserves one call.
   *
   * @param operation what the call is for, e.g. {@code eod history}
   * @return {@code true} when the call may be made
   */
  public boolean tryReserve(String operation) {
    int budget = settings.dailyCallBudget();
    String safeOperation = LogSanitizer.oneLine(operation);
    LocalDate day = LocalDate.now(clock.withZone(ZoneOffset.UTC));
    OptionalInt calls = counter.reserve(day, budget);
    if (calls.isEmpty()) {
      LOG.info(
          "EODHD daily budget exhausted, call postponed: operation={}, budget={}",
          safeOperation,
          budget);
      return false;
    }
    int used = calls.getAsInt();
    LOG.info("EODHD call: operation={}, calls={}/{}", safeOperation, used, budget);
    return true;
  }
}
