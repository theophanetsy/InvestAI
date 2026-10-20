package dev.investai.market.application;

import dev.investai.market.domain.DailyPriceRepository;
import dev.investai.market.domain.EuronextCalendar;
import dev.investai.market.domain.IndicatorRepository;
import dev.investai.market.domain.IndicatorRun;
import dev.investai.market.domain.IndicatorSet;
import dev.investai.market.domain.Instrument;
import dev.investai.market.domain.InstrumentId;
import dev.investai.market.domain.InstrumentRepository;
import dev.investai.market.domain.YearWindow;
import dev.investai.shared.logging.LogSanitizer;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Daily computation of the indicators of every instrument of the reference data (spec 2.4,
 * criterion 6), run by the market jobs after the collection and the EODHD maintenance. Idempotent:
 * nothing is computed when the stored indicators already cover the latest session and no close was
 * written since; otherwise every row is replaced. The reference date is the latest session with a
 * close before today (Paris): a close of today (EODHD, evening) waits for the next day, so that
 * every instrument has the same last session.
 */
@Service
public class IndicatorComputation {

  /** What a run did. */
  public enum Outcome {
    /** Every instrument has new indicators. */
    COMPUTED,
    /** The stored indicators are up to date. */
    UP_TO_DATE,
    /** No close is stored yet. */
    NO_CLOSES,
    /** Another run is in progress. */
    BUSY
  }

  private static final Logger LOG = LoggerFactory.getLogger(IndicatorComputation.class);

  private final EuronextCalendar calendar = new EuronextCalendar();
  private final ReentrantLock running = new ReentrantLock();
  private final InstrumentRepository instruments;
  private final DailyPriceRepository prices;
  private final IndicatorRepository indicators;
  private final YearOfClosesLoader loader;
  private final Clock clock;

  /**
   * Creates the computation.
   *
   * @param instruments reference data
   * @param prices stored closes
   * @param indicators stored indicators
   * @param settings benchmarks
   * @param clock current time
   */
  public IndicatorComputation(
      InstrumentRepository instruments,
      DailyPriceRepository prices,
      IndicatorRepository indicators,
      IndicatorSettings settings,
      Clock clock) {
    this.instruments = instruments;
    this.prices = prices;
    this.indicators = indicators;
    this.loader = new YearOfClosesLoader(instruments, prices, settings);
    this.clock = clock;
  }

  /**
   * Computes the indicators of every instrument when they are stale.
   *
   * @return what was done
   */
  public Outcome refresh() {
    if (!running.tryLock()) {
      return Outcome.BUSY;
    }
    try {
      return refreshWhileLocked();
    } finally {
      running.unlock();
    }
  }

  private Outcome refreshWhileLocked() {
    Instant startedAt = clock.instant();
    Optional<LocalDate> asOf =
        prices.latestSessionBefore(LocalDate.ofInstant(startedAt, EuronextCalendar.ZONE));
    if (asOf.isEmpty()) {
      return Outcome.NO_CLOSES;
    }
    if (upToDate(asOf.get())) {
      return Outcome.UP_TO_DATE;
    }
    compute(asOf.get(), startedAt);
    return Outcome.COMPUTED;
  }

  private boolean upToDate(LocalDate asOf) {
    Optional<IndicatorRun> last = indicators.lastRun();
    if (last.isEmpty() || !last.get().asOf().equals(asOf)) {
      return false;
    }
    Instant computedAt = last.get().computedAt();
    return prices.lastWrite().map(write -> !write.isAfter(computedAt)).orElse(true);
  }

  /** Stamped with its start: a close written during the run makes the next run compute again. */
  private void compute(LocalDate asOf, Instant startedAt) {
    long start = System.nanoTime();
    YearWindow window = YearWindow.ending(asOf, calendar);
    List<InstrumentId> ids = instruments.findAll().stream().map(Instrument::id).toList();
    Map<InstrumentId, IndicatorSet> computed = loader.indicators(window, ids);
    indicators.saveAll(computed, startedAt);
    long millis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
    int count = computed.size();
    String session = LogSanitizer.oneLine(asOf);
    LOG.info("Indicators computed: instruments={}, asOf={}, durationMs={}", count, session, millis);
  }
}
