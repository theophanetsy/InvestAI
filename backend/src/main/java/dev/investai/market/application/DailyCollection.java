package dev.investai.market.application;

import dev.investai.market.domain.CollectionJournal;
import dev.investai.market.domain.EuronextCalendar;
import dev.investai.market.domain.Instrument;
import dev.investai.market.domain.InstrumentId;
import dev.investai.market.domain.InstrumentRepository;
import dev.investai.market.domain.MarketDataUnavailableException;
import dev.investai.market.domain.SessionCloseCalculator;
import dev.investai.market.domain.SessionCloses;
import dev.investai.market.domain.TradeFileSegment;
import dev.investai.market.domain.TradeFileSource;
import dev.investai.shared.logging.LogSanitizer;
import java.time.Clock;
import java.time.LocalDate;
import java.time.Period;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Daily collection of closes from the Euronext deferred trade files (spec 2.1, criteria 9 to 13):
 * expected session = previous trading day; segments already collected are not downloaded again; a
 * file of another session is not written (retried at the next run); after a complete collection,
 * missed sessions are queued for an EODHD catch-up.
 */
@Service
public class DailyCollection {

  /** What a run did. */
  public enum Outcome {
    /** Both segments of the expected session are now collected. */
    COLLECTED,
    /** Nothing to download. */
    ALREADY_COLLECTED,
    /** A file still holds an older session. */
    NOT_YET_AVAILABLE,
    /** A file could not be downloaded or read. */
    FAILED,
    /** Another run is in progress. */
    BUSY
  }

  private static final Logger LOG = LoggerFactory.getLogger(DailyCollection.class);

  /** EODHD only keeps one year of history on the free plan. */
  private static final Period CATCH_UP_WINDOW = Period.ofYears(1);

  private final EuronextCalendar calendar = new EuronextCalendar();
  private final ReentrantLock running = new ReentrantLock();
  private final TradeFileSource files;
  private final InstrumentRepository instruments;
  private final CollectionJournal journal;
  private final PriceRecorder recorder;
  private final Clock clock;

  /**
   * Creates the collection.
   *
   * @param files the Euronext files
   * @param instruments reference data
   * @param journal collected sessions
   * @param recorder writes closes
   * @param clock current time
   */
  public DailyCollection(
      TradeFileSource files,
      InstrumentRepository instruments,
      CollectionJournal journal,
      PriceRecorder recorder,
      Clock clock) {
    this.files = files;
    this.instruments = instruments;
    this.journal = journal;
    this.recorder = recorder;
    this.clock = clock;
  }

  /**
   * Collects the expected session if needed.
   *
   * @return what was done
   */
  public Outcome collect() {
    if (!running.tryLock()) {
      return Outcome.BUSY;
    }
    try {
      LocalDate today = LocalDate.now(clock.withZone(SessionCloseCalculator.MARKET_ZONE));
      return collect(calendar.previousTradingDay(today));
    } finally {
      running.unlock();
    }
  }

  private Outcome collect(LocalDate session) {
    List<TradeFileSegment> pending =
        Arrays.stream(TradeFileSegment.values())
            .filter(segment -> !journal.isCollected(session, segment))
            .toList();
    if (pending.isEmpty()) {
      return Outcome.ALREADY_COLLECTED;
    }
    List<Instrument> tracked = instruments.findTracked();
    for (TradeFileSegment segment : pending) {
      Outcome outcome = collectSegment(session, segment, tracked);
      if (outcome != Outcome.COLLECTED) {
        return outcome;
      }
    }
    scheduleCatchUp(session, tracked);
    return Outcome.COLLECTED;
  }

  private Outcome collectSegment(
      LocalDate session, TradeFileSegment segment, List<Instrument> tracked) {
    SessionCloseCalculator calculator = new SessionCloseCalculator(tracked, session);
    TradeFileSource.Summary summary;
    try {
      summary = files.read(segment, calculator::accept);
    } catch (MarketDataUnavailableException ex) {
      String reason = LogSanitizer.oneLine(ex.getMessage());
      String file = LogSanitizer.oneLine(segment);
      LOG.warn("Euronext file not collected: segment={}, reason={}", file, reason);
      return Outcome.FAILED;
    }
    LocalDate found = summary.session();
    if (!session.equals(found)) {
      String file = LogSanitizer.oneLine(segment);
      String expected = LogSanitizer.oneLine(session);
      String actual = LogSanitizer.oneLine(found);
      LOG.info(
          "Euronext file not regenerated yet: segment={}, expected={}, found={}",
          file,
          expected,
          actual);
      return Outcome.NOT_YET_AVAILABLE;
    }
    SessionCloses result = calculator.result();
    AnomalyReport.log(session, result);
    recorder.recordSessionFile(session, segment, result.closes());
    int closes = result.closes().size();
    long trades = summary.trades();
    long rejected = summary.rejected();
    String file = LogSanitizer.oneLine(segment);
    String day = LogSanitizer.oneLine(session);
    LOG.info(
        "Euronext file collected: segment={}, session={}, trades={}, rejected={}, closes={}",
        file,
        day,
        trades,
        rejected,
        closes);
    return Outcome.COLLECTED;
  }

  /** Sessions between the last collected one and this one were missed (application off). */
  private void scheduleCatchUp(LocalDate session, List<Instrument> tracked) {
    LocalDate oldest = session.minus(CATCH_UP_WINDOW);
    List<LocalDate> missed =
        journal
            .lastSessionBefore(session)
            .map(last -> last.plusDays(1).isBefore(oldest) ? oldest : last.plusDays(1))
            .map(from -> calendar.tradingDaysBetween(from, session.minusDays(1)))
            .orElse(List.of());
    if (missed.isEmpty()) {
      return;
    }
    // Instruments without history get a full year of it, missed sessions included.
    List<InstrumentId> withHistory =
        tracked.stream().filter(Instrument::historyLoaded).map(Instrument::id).toList();
    recorder.scheduleCatchUp(missed, withHistory);
    int sessions = missed.size();
    int count = withHistory.size();
    LOG.info(
        "Missed sessions queued for EODHD catch-up: sessions={}, instruments={}", sessions, count);
  }
}
