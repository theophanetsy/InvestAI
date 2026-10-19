package dev.investai.market.application;

import dev.investai.market.domain.HistoricalClose;
import dev.investai.market.domain.Instrument;
import dev.investai.market.domain.Isin;
import dev.investai.market.domain.ListedInstrument;
import dev.investai.market.domain.MarketDataProvider;
import dev.investai.market.domain.MarketDataUnavailableException;
import dev.investai.market.domain.ReferenceData;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** EODHD answering from memory and recording its calls. */
final class FakeEodhd implements MarketDataProvider {
  private final List<String> calls = new ArrayList<>();
  private final Map<Isin, List<HistoricalClose>> history = new HashMap<>();
  private List<ListedInstrument> reference = List.of();
  private boolean enabled = true;
  private boolean failing;

  void reference(ListedInstrument... instruments) {
    reference = List.of(instruments);
  }

  void history(Isin isin, HistoricalClose... closes) {
    history.put(isin, List.of(closes));
  }

  void enabled(boolean value) {
    enabled = value;
  }

  void failing(boolean value) {
    failing = value;
  }

  List<String> calls() {
    return calls;
  }

  @Override
  public boolean isEnabled() {
    return enabled;
  }

  @Override
  public ReferenceData referenceData() {
    calls.add("exchange-symbol-list");
    failIfAsked();
    return new ReferenceData(reference, 1);
  }

  @Override
  public List<HistoricalClose> dailyCloses(Instrument instrument, LocalDate from, LocalDate to) {
    calls.add("eod " + instrument.ticker() + " " + from + " " + to);
    failIfAsked();
    return history.getOrDefault(instrument.listing().isin(), List.of()).stream()
        .filter(close -> !close.date().isBefore(from) && !close.date().isAfter(to))
        .toList();
  }

  private void failIfAsked() {
    if (failing) {
      throw new MarketDataUnavailableException("EODHD eod failed: ResourceAccessException");
    }
  }
}
