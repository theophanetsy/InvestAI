package dev.investai.market.application;

import dev.investai.market.domain.CatchUpQueue;
import dev.investai.market.domain.CatchUpQueue.CatchUpRequest;
import dev.investai.market.domain.CollectionJournal;
import dev.investai.market.domain.DailyClose;
import dev.investai.market.domain.DailyPriceRepository;
import dev.investai.market.domain.InstrumentId;
import dev.investai.market.domain.InstrumentRepository;
import dev.investai.market.domain.PriceKey;
import dev.investai.market.domain.PriceMerge;
import dev.investai.market.domain.TradeFileSegment;
import dev.investai.shared.logging.LogSanitizer;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Writes closes with the source priority of ADR-0009 ({@link PriceMerge}), each time in the same
 * transaction as the bookkeeping that goes with them (collection journal, catch-up queue, history
 * flag): a crash never leaves a session marked as collected without its closes.
 */
@Service
public class PriceRecorder {

  private static final Logger LOG = LoggerFactory.getLogger(PriceRecorder.class);

  private final DailyPriceRepository prices;
  private final CollectionJournal journal;
  private final CatchUpQueue queue;
  private final InstrumentRepository instruments;
  private final Clock clock;

  /**
   * Creates the recorder.
   *
   * @param prices stored closes
   * @param journal collected sessions
   * @param queue pending catch-ups
   * @param instruments reference data
   * @param clock current time
   */
  public PriceRecorder(
      DailyPriceRepository prices,
      CollectionJournal journal,
      CatchUpQueue queue,
      InstrumentRepository instruments,
      Clock clock) {
    this.prices = prices;
    this.journal = journal;
    this.queue = queue;
    this.instruments = instruments;
    this.clock = clock;
  }

  /**
   * Records the closes of a Euronext file and marks the file as collected.
   *
   * @param session the session of the file
   * @param segment the file segment
   * @param closes the closes computed from the file
   */
  @Transactional
  public void recordSessionFile(
      LocalDate session, TradeFileSegment segment, List<DailyClose> closes) {
    record(closes);
    journal.recordFile(session, segment, closes.size(), clock.instant());
  }

  /**
   * Records the history of an instrument and flags it as loaded.
   *
   * @param instrument the instrument
   * @param closes its closes
   */
  @Transactional
  public void recordHistory(InstrumentId instrument, List<DailyClose> closes) {
    record(closes);
    instruments.markHistoryLoaded(instrument, clock.instant());
  }

  /**
   * Records the closes of a catch-up and removes it from the queue.
   *
   * @param request the catch-up done
   * @param closes the closes received
   */
  @Transactional
  public void recordCatchUp(CatchUpRequest request, List<DailyClose> closes) {
    record(closes);
    queue.remove(request);
    journal.refreshCatchUpCounts(request.from(), request.to());
  }

  /**
   * Records missed sessions as caught up with EODHD and queues their closes for each instrument.
   *
   * @param missed the missed sessions, in chronological order, not empty
   * @param instrumentIds instruments whose history needs the missed closes
   */
  @Transactional
  public void scheduleCatchUp(List<LocalDate> missed, List<InstrumentId> instrumentIds) {
    Instant now = clock.instant();
    journal.recordCatchUp(missed, now);
    if (!instrumentIds.isEmpty()) {
      queue.enqueue(instrumentIds, missed.getFirst(), missed.getLast(), now);
    }
  }

  private void record(List<DailyClose> closes) {
    if (closes.isEmpty()) {
      return;
    }
    Set<InstrumentId> ids =
        closes.stream().map(DailyClose::instrumentId).collect(Collectors.toSet());
    LocalDate from =
        closes.stream().map(DailyClose::tradingDate).min(Comparator.naturalOrder()).orElseThrow();
    LocalDate to =
        closes.stream().map(DailyClose::tradingDate).max(Comparator.naturalOrder()).orElseThrow();
    PriceMerge merge = PriceMerge.of(closes, prices.find(ids, from, to));
    prices.save(merge.toWrite(), clock.instant());
    reportConflicts(merge.conflicts());
  }

  /**
   * One line per write, like {@link AnomalyReport}: tracking an instrument with months of Euronext
   * closes may disagree on many sessions. Instrument and date only: prices are not logged.
   */
  private static void reportConflicts(List<PriceKey> conflicts) {
    if (conflicts.isEmpty()) {
      return;
    }
    int count = conflicts.size();
    String examples =
        LogSanitizer.oneLine(
            conflicts.stream()
                .map(key -> key.instrumentId() + "@" + key.tradingDate())
                .sorted()
                .limit(AnomalyReport.MAX_EXAMPLES)
                .collect(Collectors.joining(", ")));
    LOG.warn("Euronext and EODHD closes differ: count={}, closes=[{}]", count, examples);
  }
}
