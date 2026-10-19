package dev.investai.market.application;

import dev.investai.market.domain.CatchUpQueue;
import dev.investai.market.domain.CatchUpQueue.CatchUpRequest;
import dev.investai.market.domain.DailyClose;
import dev.investai.market.domain.EuronextCalendar;
import dev.investai.market.domain.HistoricalClose;
import dev.investai.market.domain.Instrument;
import dev.investai.market.domain.InstrumentId;
import dev.investai.market.domain.InstrumentRepository;
import dev.investai.market.domain.MarketDataProvider;
import dev.investai.market.domain.MarketDataUnavailableException;
import dev.investai.market.domain.PriceSource;
import dev.investai.market.domain.Prices;
import dev.investai.shared.logging.LogSanitizer;
import java.time.Clock;
import java.time.LocalDate;
import java.time.Period;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Loads closes from EODHD, one call per instrument (spec 2.1, criteria 8 and 13): catch-up of
 * missed sessions, then one year of history for newly tracked instruments. Raw closes only. A run
 * stops at the first failure; the failed instrument is postponed to the next day, so that a
 * permanent error (delisted instrument) never takes the whole budget nor blocks the others.
 */
@Service
public class PriceHistoryLoader {

  /** Outcome of a step. */
  public enum Step {
    /** Everything pending was done. */
    COMPLETED,
    /** Stopped: budget exhausted or provider failure; the rest waits for the next run. */
    STOPPED
  }

  private static final Logger LOG = LoggerFactory.getLogger(PriceHistoryLoader.class);
  private static final Period HISTORY = Period.ofYears(1);

  private final MarketDataProvider provider;
  private final CallBudget budget;
  private final InstrumentRepository instruments;
  private final CatchUpQueue queue;
  private final PriceRecorder recorder;
  private final Clock clock;
  private final EuronextCalendar calendar = new EuronextCalendar();

  /** Instruments whose last call failed, by UTC day: not retried before the next day. */
  private final Map<InstrumentId, LocalDate> postponed = new ConcurrentHashMap<>();

  /**
   * Creates the loader.
   *
   * @param provider EODHD
   * @param budget daily call budget
   * @param instruments reference data
   * @param queue pending catch-ups
   * @param recorder writes closes
   * @param clock current time
   */
  public PriceHistoryLoader(
      MarketDataProvider provider,
      CallBudget budget,
      InstrumentRepository instruments,
      CatchUpQueue queue,
      PriceRecorder recorder,
      Clock clock) {
    this.provider = provider;
    this.budget = budget;
    this.instruments = instruments;
    this.queue = queue;
    this.recorder = recorder;
    this.clock = clock;
  }

  /**
   * Fetches the closes of missed sessions.
   *
   * @return whether the queue was emptied
   */
  public Step catchUp() {
    for (CatchUpRequest request : queue.pending()) {
      Optional<Instrument> instrument = instruments.findById(request.instrumentId());
      if (instrument.isPresent()
          && !isPostponed(request.instrumentId())
          && catchUp(instrument.get(), request) == Step.STOPPED) {
        return Step.STOPPED;
      }
    }
    return Step.COMPLETED;
  }

  /**
   * Loads one year of closes for tracked instruments without history.
   *
   * @return whether every tracked instrument now has its history
   */
  public Step loadMissingHistories() {
    for (Instrument instrument : instruments.findTracked()) {
      if (!instrument.historyLoaded()
          && !isPostponed(instrument.id())
          && loadHistory(instrument) == Step.STOPPED) {
        return Step.STOPPED;
      }
    }
    return Step.COMPLETED;
  }

  private Step catchUp(Instrument instrument, CatchUpRequest request) {
    Optional<List<DailyClose>> closes =
        fetch("eod catch-up", instrument, request.from(), request.to());
    closes.ifPresent(received -> recorder.recordCatchUp(request, received));
    return closes.isPresent() ? Step.COMPLETED : Step.STOPPED;
  }

  private Step loadHistory(Instrument instrument) {
    LocalDate today = LocalDate.now(clock.withZone(EuronextCalendar.ZONE));
    // Up to the previous session: a bar of the day may still be moving.
    LocalDate last = calendar.previousTradingDay(today);
    Optional<List<DailyClose>> closes =
        fetch("eod history", instrument, today.minus(HISTORY), last);
    closes.ifPresent(received -> recordHistory(instrument, received));
    return closes.isPresent() ? Step.COMPLETED : Step.STOPPED;
  }

  private boolean isPostponed(InstrumentId instrument) {
    return budgetDay().equals(postponed.get(instrument));
  }

  /** Day of the EODHD quota (UTC). */
  private LocalDate budgetDay() {
    return LocalDate.now(clock.withZone(ZoneOffset.UTC));
  }

  private void recordHistory(Instrument instrument, List<DailyClose> closes) {
    recorder.recordHistory(instrument.id(), closes);
    String listing = LogSanitizer.oneLine(instrument.listing());
    int count = closes.size();
    LOG.info("History loaded: listing={}, closes={}", listing, count);
  }

  /** One budgeted call; empty when the budget is exhausted or the call failed. */
  private Optional<List<DailyClose>> fetch(
      String operation, Instrument instrument, LocalDate from, LocalDate to) {
    if (!budget.tryReserve(operation)) {
      return Optional.empty();
    }
    try {
      return Optional.of(toCloses(instrument, provider.dailyCloses(instrument, from, to)));
    } catch (MarketDataUnavailableException ex) {
      postponed.put(instrument.id(), budgetDay());
      String listing = LogSanitizer.oneLine(instrument.listing());
      String reason = LogSanitizer.oneLine(ex.getMessage());
      LOG.warn("EODHD call failed, retried tomorrow: listing={}, reason={}", listing, reason);
      return Optional.empty();
    }
  }

  private static List<DailyClose> toCloses(Instrument instrument, List<HistoricalClose> history) {
    List<DailyClose> closes =
        history.stream()
            .flatMap(
                bar ->
                    Prices.exact(bar.close())
                        .map(
                            close ->
                                new DailyClose(
                                    instrument.id(),
                                    bar.date(),
                                    close,
                                    instrument.currency(),
                                    PriceSource.EODHD))
                        .stream())
            .toList();
    int skipped = history.size() - closes.size();
    if (skipped > 0) {
      String listing = LogSanitizer.oneLine(instrument.listing());
      LOG.warn(
          "EODHD closes not storable at scale 4 skipped: listing={}, count={}", listing, skipped);
    }
    return closes;
  }
}
