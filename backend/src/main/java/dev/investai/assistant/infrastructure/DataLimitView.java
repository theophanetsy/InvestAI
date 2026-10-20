package dev.investai.assistant.infrastructure;

import dev.investai.assistant.domain.DataLimit;
import java.util.Arrays;
import java.util.List;

/**
 * A limit of the data recalled in a tool output (spec 3.1, criterion 6).
 *
 * @param code stable code
 * @param text constant French text of the application
 */
public record DataLimitView(DataLimit code, String text) {

  /** Limits of the closes and indicators of an instrument (getInstrument, getBenchmarks). */
  static final List<DataLimitView> INSTRUMENT =
      of(
          DataLimit.RAW_CLOSES,
          DataLimit.EXCLUDES_DIVIDENDS,
          DataLimit.OUTLIER_QUOTES,
          DataLimit.SHORT_HISTORY,
          DataLimit.LOW_LIQUIDITY,
          DataLimit.PEA_ELIGIBILITY_INDICATIVE);

  static List<DataLimitView> of(DataLimit... limits) {
    return Arrays.stream(limits).map(limit -> new DataLimitView(limit, limit.text())).toList();
  }
}
