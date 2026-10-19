package dev.investai.market.infrastructure;

import dev.investai.market.domain.HistoricalClose;
import dev.investai.market.domain.Instrument;
import dev.investai.market.domain.MarketDataProvider;
import dev.investai.market.domain.MarketDataUnavailableException;
import dev.investai.market.domain.ReferenceData;
import java.time.LocalDate;
import java.util.List;

/** Stands for EODHD when {@code INVESTAI_EODHD_API_KEY} is not set: its features are off. */
final class DisabledMarketDataProvider implements MarketDataProvider {

  private static final String DISABLED = "EODHD disabled: INVESTAI_EODHD_API_KEY is not set";

  @Override
  public boolean isEnabled() {
    return false;
  }

  @Override
  public ReferenceData referenceData() {
    throw new MarketDataUnavailableException(DISABLED);
  }

  @Override
  public List<HistoricalClose> dailyCloses(Instrument instrument, LocalDate from, LocalDate to) {
    throw new MarketDataUnavailableException(DISABLED);
  }
}
