package dev.investai.market.domain;

import java.time.LocalDate;
import java.util.function.Consumer;
import org.jspecify.annotations.Nullable;

/** Port: the Euronext deferred trade files of the previous trading day (MiFIR, ADR-0009). */
public interface TradeFileSource {

  /**
   * Downloads a file and streams its trades, in file order.
   *
   * @param segment the file segment
   * @param trades receives each valid trade
   * @return what the file contained
   * @throws MarketDataUnavailableException when the file cannot be downloaded or read
   */
  Summary read(TradeFileSegment segment, Consumer<MifirTrade> trades);

  /**
   * Content of a trade file.
   *
   * @param session latest session found in the file, {@code null} when it has no valid trade
   * @param trades number of valid trades
   * @param rejected number of malformed lines, skipped
   */
  record Summary(@Nullable LocalDate session, long trades, long rejected) {}
}
