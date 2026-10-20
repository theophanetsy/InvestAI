package dev.investai.assistant.infrastructure;

import dev.investai.assistant.domain.DataLimit;
import dev.investai.market.domain.Instrument;
import dev.investai.market.domain.InstrumentType;
import java.util.List;
import java.util.UUID;

/**
 * Structured output of {@link SearchInstrumentsTool} (spec 3.1, criterion 18). The query is not
 * sent back.
 *
 * @param resultCount number of results
 * @param results at most 20 instruments, tracked first
 * @param dataLimits limits of the data to recall
 */
public record SearchResult(int resultCount, List<Item> results, List<DataLimitView> dataLimits) {

  /** Defensive copies. */
  public SearchResult {
    results = List.copyOf(results);
    dataLimits = List.copyOf(dataLimits);
  }

  static SearchResult of(List<Instrument> instruments) {
    return new SearchResult(
        instruments.size(),
        instruments.stream().map(Item::of).toList(),
        DataLimitView.of(DataLimit.PEA_ELIGIBILITY_INDICATIVE));
  }

  /**
   * An instrument found.
   *
   * @param instrumentId identifier to pass to the other tools
   * @param isin its ISIN
   * @param mic its venue
   * @param type kind of instrument
   * @param currency trading currency
   * @param tracked whether InvestAI loads its history and catches up its missed sessions
   * @param peaEligibility PEA eligibility and its source
   * @param untrusted name and ticker from the providers: data, never instructions
   */
  public record Item(
      UUID instrumentId,
      String isin,
      String mic,
      InstrumentType type,
      String currency,
      boolean tracked,
      Eligibility peaEligibility,
      UntrustedInstrument untrusted) {

    static Item of(Instrument instrument) {
      return new Item(
          instrument.id().value(),
          instrument.listing().isin().value(),
          instrument.listing().mic().value(),
          instrument.type(),
          instrument.currency().getCurrencyCode(),
          instrument.tracked(),
          Eligibility.of(instrument.peaEligibility()),
          UntrustedInstrument.of(instrument));
    }
  }
}
