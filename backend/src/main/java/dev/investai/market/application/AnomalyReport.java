package dev.investai.market.application;

import dev.investai.market.domain.Listing;
import dev.investai.market.domain.SessionCloses;
import dev.investai.shared.logging.LogSanitizer;
import java.time.LocalDate;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Logs the anomalies of a collected file (spec 2.1, criterion 10), never with prices. With the
 * whole reference data (~1,300 listings, spec 2.2), one line per kind of anomaly: the number of
 * listings and the first ones by ISIN.
 */
final class AnomalyReport {

  /** Listings named in a line, enough to investigate. */
  static final int MAX_EXAMPLES = 10;

  private static final Logger LOG = LoggerFactory.getLogger(AnomalyReport.class);

  private AnomalyReport() {}

  static void log(LocalDate session, SessionCloses result) {
    logKind(
        "Last trades share a timestamp with different prices, file order kept",
        session,
        result.ambiguousTimestamps());
    logKind(
        "Instruments traded on another venue only, check their MIC",
        session,
        result.tradedElsewhereOnly());
    logKind("Closes with more than 4 decimal places, not stored", session, result.notStorable());
    logKind(
        "Trades in another currency than the instrument, ignored", session, result.otherCurrency());
  }

  private static void logKind(String anomaly, LocalDate session, Set<Listing> listings) {
    if (listings.isEmpty()) {
      return;
    }
    String kind = LogSanitizer.oneLine(anomaly);
    String day = LogSanitizer.oneLine(session);
    int count = listings.size();
    String examples =
        LogSanitizer.oneLine(
            listings.stream()
                .map(Listing::toString)
                .sorted()
                .limit(MAX_EXAMPLES)
                .collect(Collectors.joining(", ")));
    LOG.warn("{}: session={}, count={}, listings=[{}]", kind, day, count, examples);
  }
}
