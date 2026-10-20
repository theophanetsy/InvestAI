package dev.investai.market.application;

import dev.investai.market.domain.CollectionJournal;
import dev.investai.market.domain.DailyPriceRepository;
import dev.investai.market.domain.EuronextCalendar;
import dev.investai.market.domain.IndicatorRepository;
import dev.investai.market.domain.IndicatorScreen;
import dev.investai.market.domain.Instrument;
import dev.investai.market.domain.InstrumentId;
import dev.investai.market.domain.InstrumentIndicators;
import dev.investai.market.domain.InstrumentRepository;
import dev.investai.market.domain.Screening;
import dev.investai.market.domain.ScreeningCriteria;
import dev.investai.market.domain.StoredIndicators;
import dev.investai.market.domain.YearOfCloses;
import dev.investai.market.domain.YearWindow;
import dev.investai.shared.domain.ResourceNotFoundException;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Read side of the indicators (spec 2.4, criteria 6 and 9): the stored daily indicators, and the
 * closes the portfolio indicators are computed from. Everything comes from the database; the
 * indicators of every instrument are never computed again here.
 */
@Service
@Transactional(readOnly = true)
public class IndicatorQueries {

  /** Error code: the instrument exists but its indicators were not computed yet. */
  public static final String NOT_COMPUTED = "indicators.not-computed";

  private final EuronextCalendar calendar = new EuronextCalendar();
  private final InstrumentRepository instruments;
  private final DailyPriceRepository prices;
  private final IndicatorRepository indicators;
  private final YearOfClosesLoader loader;
  private final Clock clock;

  /**
   * Creates the queries.
   *
   * @param instruments reference data
   * @param prices stored closes
   * @param journal collected sessions
   * @param indicators stored indicators
   * @param settings benchmarks
   * @param clock current time
   */
  public IndicatorQueries(
      InstrumentRepository instruments,
      DailyPriceRepository prices,
      CollectionJournal journal,
      IndicatorRepository indicators,
      IndicatorSettings settings,
      Clock clock) {
    this.instruments = instruments;
    this.prices = prices;
    this.indicators = indicators;
    this.loader = new YearOfClosesLoader(instruments, prices, journal, settings);
    this.clock = clock;
  }

  /**
   * Returns the stored indicators of an instrument.
   *
   * @param id the instrument
   * @return the instrument and its indicators
   * @throws ResourceNotFoundException {@code instrument.not-found} when unknown, {@link
   *     #NOT_COMPUTED} before the first computation that saw it
   */
  public InstrumentIndicators instrument(InstrumentId id) {
    Instrument instrument = find(id);
    StoredIndicators stored =
        indicators
            .find(id)
            .orElseThrow(
                () ->
                    new ResourceNotFoundException(
                        NOT_COMPUTED, "Indicators not computed yet: " + id));
    return new InstrumentIndicators(instrument, stored);
  }

  /**
   * Returns everything stored about an instrument (MCP tool {@code getInstrument}, spec 3.1,
   * criterion 20): its latest close and, once computed, its indicators. Never tracks it.
   *
   * @param id the instrument
   * @return the instrument, its latest close and its indicators
   * @throws ResourceNotFoundException {@code instrument.not-found} when unknown
   */
  public InstrumentSnapshot snapshot(InstrumentId id) {
    Instrument instrument = find(id);
    return new InstrumentSnapshot(
        instrument, prices.findLatest(id).orElse(null), indicators.find(id).orElse(null));
  }

  /**
   * Screens every instrument on its stored indicators.
   *
   * @param criteria the filter, order and limit
   * @return the matching instruments
   */
  public Screening screen(ScreeningCriteria criteria) {
    Map<InstrumentId, Instrument> byId =
        instruments.findAll().stream()
            .collect(Collectors.toMap(Instrument::id, Function.identity()));
    List<InstrumentIndicators> candidates =
        indicators.findAll().stream()
            .flatMap(
                stored ->
                    Optional.ofNullable(byId.get(stored.instrumentId()))
                        .map(instrument -> new InstrumentIndicators(instrument, stored))
                        .stream())
            .toList();
    return IndicatorScreen.apply(candidates, criteria);
  }

  /**
   * Loads one year of closes of some instruments and of the benchmarks, up to the latest session
   * before today (the reference date of the daily computation), to compute indicators on demand
   * (the declared portfolio).
   *
   * @param ids the instruments
   * @return the closes, empty when no close is stored at all
   */
  public Optional<YearOfCloses> yearOfCloses(Collection<InstrumentId> ids) {
    LocalDate today = LocalDate.now(clock.withZone(EuronextCalendar.ZONE));
    return prices
        .latestSessionBefore(today)
        .map(asOf -> loader.load(YearWindow.ending(asOf, calendar), ids));
  }

  private Instrument find(InstrumentId id) {
    return instruments
        .findById(id)
        .orElseThrow(
            () ->
                new ResourceNotFoundException(
                    InstrumentTracking.NOT_FOUND, "Unknown instrument: " + id));
  }
}
