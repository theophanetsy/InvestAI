package dev.investai.market.application;

import dev.investai.market.domain.DailyClose;
import dev.investai.market.domain.DailyPriceRepository;
import dev.investai.market.domain.Instrument;
import dev.investai.market.domain.InstrumentId;
import dev.investai.market.domain.InstrumentRepository;
import dev.investai.market.domain.InvalidPriceRangeException;
import dev.investai.market.domain.PriceRange;
import dev.investai.market.domain.SessionCloseCalculator;
import dev.investai.shared.domain.ResourceNotFoundException;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
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
   * Lists the closes of an instrument over a period.
   *
   * @param id the instrument
   * @param from first session, default one year before {@code to}
   * @param to last session, default today
   * @return closes by ascending date
   * @throws InvalidPriceRangeException when the period is invalid
   * @throws ResourceNotFoundException when the instrument is unknown
   */
  public List<DailyClose> closes(
      InstrumentId id, @Nullable LocalDate from, @Nullable LocalDate to) {
    LocalDate today = LocalDate.now(clock.withZone(SessionCloseCalculator.MARKET_ZONE));
    PriceRange range = PriceRange.resolve(from, to, today);
    find(id);
    return prices.findBetween(id, range.from(), range.to());
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
