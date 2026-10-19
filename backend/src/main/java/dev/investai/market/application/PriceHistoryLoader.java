package dev.investai.market.application;

import dev.investai.market.domain.CatchUpQueue;
import dev.investai.market.domain.CatchUpQueue.CatchUpRequest;
import dev.investai.market.domain.DailyClose;
import dev.investai.market.domain.HistoricalClose;
import dev.investai.market.domain.Instrument;
import dev.investai.market.domain.InstrumentRepository;
import dev.investai.market.domain.MarketDataProvider;
import dev.investai.market.domain.MarketDataUnavailableException;
import dev.investai.market.domain.PriceSource;
import dev.investai.market.domain.Prices;
import dev.investai.market.domain.SessionCloseCalculator;
import java.time.Clock;
import java.time.LocalDate;
import java.time.Period;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Loads closes from EODHD, one call per instrument (spec 2.1, criteria 8 and 13): catch-up of
 * missed sessions, then one year of history for newly tracked instruments. Raw closes only.
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
      if (instrument.isPresent() && catchUp(instrument.get(), request) == Step.STOPPED) {
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
      if (!instrument.historyLoaded() && loadHistory(instrument) == Step.STOPPED) {
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
    LocalDate today = LocalDate.now(clock.withZone(SessionCloseCalculator.MARKET_ZONE));
    Optional<List<DailyClose>> closes =
        fetch("eod history", instrument, today.minus(HISTORY), today);
    closes.ifPresent(received -> recordHistory(instrument, received));
    return closes.isPresent() ? Step.COMPLETED : Step.STOPPED;
  }

  private void recordHistory(Instrument instrument, List<DailyClose> closes) {
    recorder.recordHistory(instrument.id(), closes);
    String listing = instrument.listing().toString();
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
      String listing = instrument.listing().toString();
      String reason = ex.getMessage();
      LOG.warn(
          "EODHD call failed, retried at the next run: listing={}, reason={}", listing, reason);
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
      String listing = instrument.listing().toString();
      LOG.warn(
          "EODHD closes not storable at scale 4 skipped: listing={}, count={}", listing, skipped);
    }
    return closes;
  }
}
