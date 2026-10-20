package dev.investai.market.application;

import dev.investai.market.domain.DailyClose;
import dev.investai.market.domain.DailyPriceRepository;
import dev.investai.market.domain.EuronextCalendar;
import dev.investai.market.domain.IndicatorWindow;
import dev.investai.market.domain.Instrument;
import dev.investai.market.domain.InstrumentId;
import dev.investai.market.domain.InstrumentRepository;
import dev.investai.market.domain.InvalidPriceRangeException;
import dev.investai.market.domain.PriceRange;
import dev.investai.shared.domain.ResourceNotFoundException;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Read side of the market module: everything comes from the database, never from a provider
 * (ADR-0009).
 */
@Service
@Transactional(readOnly = true)
public class MarketQueries {

  /** Maximum number of search results. */
  public static final int MAX_RESULTS = 20;

  private final EuronextCalendar calendar = new EuronextCalendar();
  private final InstrumentRepository instruments;
  private final DailyPriceRepository prices;
  private final Clock clock;

  /**
   * Creates the queries.
   *
   * @param instruments reference data
   * @param prices stored closes
   * @param clock current time
   */
  public MarketQueries(InstrumentRepository instruments, DailyPriceRepository prices, Clock clock) {
    this.instruments = instruments;
    this.prices = prices;
    this.clock = clock;
  }

  /**
   * Searches the local reference data (spec 2.1, criterion 6).
   *
   * @param query ISIN, ticker or name fragment
   * @return at most {@link #MAX_RESULTS} instruments, tracked first, then by name
   */
  public List<Instrument> search(String query) {
    return instruments.search(query, MAX_RESULTS);
  }

  /**
   * Returns an instrument with its latest close.
   *
   * @param id the instrument
   * @return the details
   * @throws ResourceNotFoundException when unknown
   */
  public InstrumentDetails details(InstrumentId id) {
    Instrument instrument = find(id);
    return new InstrumentDetails(instrument, prices.findLatest(id).orElse(null));
  }

  /**
   * Returns several instruments with their latest close, in two queries whatever their number (the
   * declared portfolio, the tools of the agent).
   *
   * @param ids the instruments
   * @return the details of the known instruments, by identifier; unknown ones are absent
   */
  public Map<InstrumentId, InstrumentDetails> details(Collection<InstrumentId> ids) {
    Map<InstrumentId, DailyClose> lastCloses = prices.findLatest(ids);
    return instruments.findAllById(ids).stream()
        .map(instrument -> new InstrumentDetails(instrument, lastCloses.get(instrument.id())))
        .collect(Collectors.toMap(details -> details.instrument().id(), Function.identity()));
  }

  /**
   * Lists the closes of an instrument over a period.
   *
   * @param id the instrument
   * @param from first session, default one year before {@code to}
   * @param to last session, default today
   * @return closes by ascending date
   * @throws ResourceNotFoundException when the instrument is unknown (checked first)
   * @throws InvalidPriceRangeException when the period is invalid
   */
  public List<DailyClose> closes(
      InstrumentId id, @Nullable LocalDate from, @Nullable LocalDate to) {
    LocalDate today = LocalDate.now(clock.withZone(EuronextCalendar.ZONE));
    find(id);
    PriceRange range = PriceRange.resolve(from, to, today);
    return prices.findBetween(id, range.from(), range.to());
  }

  /**
   * Lists the raw closes of an instrument over its last sessions (MCP tool {@code getPriceHistory},
   * spec 3.1, criterion 22): the sessions of the Euronext calendar ending at its latest stored
   * close, without aggregation. Closes on a day that is not a session are left out, as the
   * indicators do.
   *
   * @param id the instrument
   * @param window the period: its sessions plus the reference session before them, like the
   *     performance of the indicators
   * @return the sessions and the closes, empty when the instrument has no close
   * @throws ResourceNotFoundException when the instrument is unknown
   */
  public PriceHistory history(InstrumentId id, IndicatorWindow window) {
    Instrument instrument = find(id);
    Optional<DailyClose> latest = prices.findLatest(id);
    if (latest.isEmpty()) {
      return new PriceHistory(instrument, List.of(), List.of());
    }
    List<LocalDate> sessions =
        calendar.sessionsEnding(latest.get().tradingDate(), window.sessions() + 1);
    Set<LocalDate> known = Set.copyOf(sessions);
    List<DailyClose> closes =
        prices.findBetween(id, sessions.getFirst(), sessions.getLast()).stream()
            .filter(close -> known.contains(close.tradingDate()))
            .toList();
    return new PriceHistory(instrument, sessions, closes);
  }

  private Instrument find(InstrumentId id) {
    return instruments
        .findById(id)
        .orElseThrow(
            () ->
                new ResourceNotFoundException(
                    InstrumentTracking.NOT_FOUND, "Unknown instrument: " + id));
  }

  /**
   * An instrument and its latest close.
   *
   * @param instrument the instrument
   * @param lastClose its most recent close, {@code null} when none is stored
   */
  public record InstrumentDetails(Instrument instrument, @Nullable DailyClose lastClose) {}
}
