package dev.investai.market.application;

import dev.investai.market.domain.ClosingPrices;
import dev.investai.market.domain.DailyClose;
import dev.investai.market.domain.DailyPriceRepository;
import dev.investai.market.domain.IndicatorSet;
import dev.investai.market.domain.InstrumentId;
import dev.investai.market.domain.InstrumentRepository;
import dev.investai.market.domain.Listing;
import dev.investai.market.domain.YearOfCloses;
import dev.investai.market.domain.YearWindow;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** Loads the closes of a {@link YearWindow} from the database, by batches of instruments. */
final class YearOfClosesLoader {

  /** Instruments per query: ~200 × 253 closes, a bounded amount of memory. */
  private static final int BATCH = 200;

  private final InstrumentRepository instruments;
  private final DailyPriceRepository prices;
  private final IndicatorSettings settings;

  YearOfClosesLoader(
      InstrumentRepository instruments, DailyPriceRepository prices, IndicatorSettings settings) {
    this.instruments = instruments;
    this.prices = prices;
    this.settings = settings;
  }

  /** Closes of some instruments and of the benchmarks, in one query for the instruments. */
  YearOfCloses load(YearWindow window, Collection<InstrumentId> ids) {
    return new YearOfCloses(
        window,
        closes(window, Set.copyOf(ids)),
        benchmark(window, settings.msciWorld()),
        benchmark(window, settings.cac40()));
  }

  /** Indicators of many instruments, computed batch by batch. */
  Map<InstrumentId, IndicatorSet> indicators(YearWindow window, List<InstrumentId> ids) {
    YearOfCloses benchmarks = load(window, List.of());
    Map<InstrumentId, IndicatorSet> result = new HashMap<>();
    for (int from = 0; from < ids.size(); from += BATCH) {
      List<InstrumentId> batch = ids.subList(from, Math.min(from + BATCH, ids.size()));
      YearOfCloses year =
          new YearOfCloses(
              window, closes(window, batch), benchmarks.msciWorld(), benchmarks.cac40());
      batch.forEach(id -> result.put(id, year.indicatorsOf(id)));
    }
    return result;
  }

  private ClosingPrices benchmark(YearWindow window, Listing listing) {
    ClosingPrices none = ClosingPrices.ofValues(window.sessions(), Map.of());
    return instruments
        .findByListing(listing)
        .map(
            benchmark -> closes(window, List.of(benchmark.id())).getOrDefault(benchmark.id(), none))
        .orElse(none);
  }

  private Map<InstrumentId, ClosingPrices> closes(YearWindow window, Collection<InstrumentId> ids) {
    Map<InstrumentId, List<DailyClose>> byInstrument =
        prices.find(ids, window.start(), window.asOf()).values().stream()
            .collect(Collectors.groupingBy(DailyClose::instrumentId));
    return byInstrument.entrySet().stream()
        .collect(
            Collectors.toMap(
                Map.Entry::getKey, entry -> ClosingPrices.of(window.sessions(), entry.getValue())));
  }
}
