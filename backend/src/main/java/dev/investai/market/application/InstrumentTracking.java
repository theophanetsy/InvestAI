package dev.investai.market.application;

import dev.investai.market.domain.Instrument;
import dev.investai.market.domain.InstrumentId;
import dev.investai.market.domain.InstrumentRepository;
import dev.investai.market.domain.Listing;
import dev.investai.shared.domain.ResourceNotFoundException;
import java.time.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Follows instruments (spec 2.1, criterion 7). Never calls a provider: the history of a newly
 * tracked instrument is loaded by the next run of the EODHD job.
 */
@Service
public class InstrumentTracking {

  /** Error code of an unknown instrument. */
  public static final String NOT_FOUND = "instrument.not-found";

  private static final Logger LOG = LoggerFactory.getLogger(InstrumentTracking.class);

  private final InstrumentRepository instruments;
  private final MarketSettings settings;
  private final Clock clock;

  /**
   * Creates the use case.
   *
   * @param instruments reference data
   * @param settings benchmarks
   * @param clock current time
   */
  public InstrumentTracking(
      InstrumentRepository instruments, MarketSettings settings, Clock clock) {
    this.instruments = instruments;
    this.settings = settings;
    this.clock = clock;
  }

  /**
   * Starts following an instrument; idempotent.
   *
   * @param id the instrument
   * @throws ResourceNotFoundException {@value #NOT_FOUND} when unknown
   */
  @Transactional
  public void track(InstrumentId id) {
    if (instruments.findById(id).isEmpty()) {
      throw new ResourceNotFoundException(NOT_FOUND, "Unknown instrument: " + id);
    }
    if (instruments.markTracked(id, clock.instant())) {
      LOG.info("Instrument tracked: instrument={}", id);
    }
  }

  /** Follows the configured benchmarks that exist in the reference data. */
  @Transactional
  public void trackBenchmarks() {
    for (Listing listing : settings.benchmarks()) {
      instruments
          .findByListing(listing)
          .filter(instrument -> !instrument.tracked())
          .ifPresent(this::trackBenchmark);
    }
  }

  private void trackBenchmark(Instrument benchmark) {
    instruments.markTracked(benchmark.id(), clock.instant());
    String listing = benchmark.listing().toString();
    LOG.info("Benchmark tracked: listing={}", listing);
  }
}
