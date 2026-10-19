package dev.investai.market.application;

import dev.investai.market.domain.InstrumentRepository;
import dev.investai.market.domain.MarketDataProvider;
import dev.investai.market.domain.MarketDataUnavailableException;
import dev.investai.market.domain.ReferenceData;
import java.time.Clock;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Loads the reference data of Euronext Paris from EODHD in one call (spec 2.1, criterion 4): when
 * the local copy is empty or older than its maximum age. Idempotent upsert keyed by listing.
 */
@Service
public class ReferenceDataLoader {

  private static final Logger LOG = LoggerFactory.getLogger(ReferenceDataLoader.class);

  private final MarketDataProvider provider;
  private final CallBudget budget;
  private final InstrumentRepository instruments;
  private final MarketSettings settings;
  private final Clock clock;

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
    boolean due =
        instruments
            .lastReferenceRefresh()
            .map(last -> last.plus(settings.referenceMaxAge()).isBefore(now))
            .orElse(true);
    if (!due || !budget.tryReserve("exchange-symbol-list")) {
      return false;
    }
    ReferenceData data;
    try {
      data = provider.referenceData();
    } catch (MarketDataUnavailableException ex) {
      String reason = ex.getMessage();
      LOG.warn("Reference data not refreshed, retried at the next run: reason={}", reason);
      return false;
    }
    instruments.upsertReference(data.instruments(), now);
    int loaded = data.instruments().size();
    int ignored = data.ignored();
    LOG.info("Reference data refreshed: instruments={}, ignored={}", loaded, ignored);
    return true;
  }
}
