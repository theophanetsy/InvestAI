package dev.investai.market.application;

import dev.investai.market.domain.InstrumentRepository;
import dev.investai.market.domain.MarketDataProvider;
import dev.investai.market.domain.MarketDataUnavailableException;
import dev.investai.market.domain.ReferenceData;
import dev.investai.shared.logging.LogSanitizer;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Loads the reference data of Euronext Paris from EODHD in one call (spec 2.1, criterion 4): when
 * the local copy is empty or older than its maximum age. Idempotent upsert keyed by listing. A
 * failed or empty download is retried the next (UTC) day, not at every hourly run.
 */
@Service
public class ReferenceDataLoader {

  private static final Logger LOG = LoggerFactory.getLogger(ReferenceDataLoader.class);

  private final MarketDataProvider provider;
  private final CallBudget budget;
  private final InstrumentRepository instruments;
  private final MarketSettings settings;
  private final Clock clock;
  private final AtomicReference<@Nullable LocalDate> failedOn = new AtomicReference<>();

  /**
   * Creates the loader.
   *
   * @param provider EODHD
   * @param budget daily call budget
   * @param instruments reference data
   * @param settings maximum age
   * @param clock current time
   */
  public ReferenceDataLoader(
      MarketDataProvider provider,
      CallBudget budget,
      InstrumentRepository instruments,
      MarketSettings settings,
      Clock clock) {
    this.provider = provider;
    this.budget = budget;
    this.instruments = instruments;
    this.settings = settings;
    this.clock = clock;
  }

  /**
   * Refreshes the reference data when due.
   *
   * @return {@code true} when it was refreshed
   */
  public boolean refreshIfDue() {
    Instant now = clock.instant();
    LocalDate day = LocalDate.now(clock.withZone(ZoneOffset.UTC));
    if (!isDue(now) || day.equals(failedOn.get()) || !budget.tryReserve("exchange-symbol-list")) {
      return false;
    }
    Optional<ReferenceData> data = download(day);
    data.ifPresent(reference -> store(reference, now));
    return data.isPresent();
  }

  private boolean isDue(Instant now) {
    return instruments
        .lastReferenceRefresh()
        .map(last -> last.plus(settings.referenceMaxAge()).isBefore(now))
        .orElse(true);
  }

  private Optional<ReferenceData> download(LocalDate day) {
    try {
      ReferenceData data = provider.referenceData();
      if (data.instruments().isEmpty()) {
        failed(day, "no usable instrument in the list");
        return Optional.empty();
      }
      return Optional.of(data);
    } catch (MarketDataUnavailableException ex) {
      failed(day, String.valueOf(ex.getMessage()));
      return Optional.empty();
    }
  }

  private void store(ReferenceData data, Instant now) {
    instruments.upsertReference(data.instruments(), now);
    int loaded = data.instruments().size();
    int ignored = data.ignored();
    LOG.info("Reference data refreshed: instruments={}, ignored={}", loaded, ignored);
  }

  private void failed(LocalDate day, String reason) {
    failedOn.set(day);
    String safeReason = LogSanitizer.oneLine(reason);
    LOG.warn("Reference data not refreshed, retried tomorrow: reason={}", safeReason);
  }
}
