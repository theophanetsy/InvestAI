package dev.investai.market.application;

import dev.investai.market.domain.BenchmarkRole;
import dev.investai.market.domain.DailyClose;
import dev.investai.market.domain.DailyPriceRepository;
import dev.investai.market.domain.IndicatorRepository;
import dev.investai.market.domain.Instrument;
import dev.investai.market.domain.InstrumentId;
import dev.investai.market.domain.InstrumentRepository;
import dev.investai.market.domain.Listing;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Read side of the benchmark ETFs (MCP tool {@code getBenchmarks}, spec 3.1, criteria 26 and 27):
 * those of {@code investai.market.benchmarks} with their role, latest close and stored indicators.
 * Everything comes from the database.
 */
@Service
@Transactional(readOnly = true)
public class BenchmarkQueries {

  /** Maximum number of benchmarks returned. */
  public static final int MAX_BENCHMARKS = 10;

  private final InstrumentRepository instruments;
  private final DailyPriceRepository prices;
  private final IndicatorRepository indicators;
  private final MarketSettings settings;
  private final IndicatorSettings references;

  /**
   * Creates the queries.
   *
   * @param instruments reference data
   * @param prices stored closes
   * @param indicators stored indicators
   * @param settings the configured benchmarks
   * @param references the benchmarks of the indicators
   */
  public BenchmarkQueries(
      InstrumentRepository instruments,
      DailyPriceRepository prices,
      IndicatorRepository indicators,
      MarketSettings settings,
      IndicatorSettings references) {
    this.instruments = instruments;
    this.prices = prices;
    this.indicators = indicators;
    this.settings = settings;
    this.references = references;
  }

  /**
   * Lists the configured benchmarks present in the reference data, at most {@value
   * #MAX_BENCHMARKS}.
   *
   * @return the benchmarks and the latest computation of the indicators
   */
  public Benchmarks benchmarks() {
    List<Instrument> found =
        settings.benchmarks().stream()
            .flatMap(listing -> instruments.findByListing(listing).stream())
            .limit(MAX_BENCHMARKS)
            .toList();
    Map<InstrumentId, DailyClose> lastCloses =
        prices.findLatest(found.stream().map(Instrument::id).toList());
    List<Benchmark> benchmarks =
        found.stream()
            .map(
                instrument ->
                    new Benchmark(
                        role(instrument.listing()),
                        new InstrumentSnapshot(
                            instrument,
                            lastCloses.get(instrument.id()),
                            indicators.find(instrument.id()).orElse(null))))
            .toList();
    return new Benchmarks(indicators.lastRun().orElse(null), benchmarks);
  }

  private BenchmarkRole role(Listing listing) {
    if (listing.equals(references.msciWorld())) {
      return BenchmarkRole.MSCI_WORLD_REFERENCE;
    }
    return listing.equals(references.cac40()) ? BenchmarkRole.CAC40_REFERENCE : BenchmarkRole.OTHER;
  }
}
