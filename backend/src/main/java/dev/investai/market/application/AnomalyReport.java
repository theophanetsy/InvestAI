package dev.investai.market.application;

import dev.investai.market.domain.Listing;
import dev.investai.market.domain.SessionCloses;
import dev.investai.shared.logging.LogSanitizer;
import java.time.LocalDate;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Logs the anomalies of a collected file (spec 2.1, criterion 10), never with prices. */
final class AnomalyReport {

  private static final Logger LOG = LoggerFactory.getLogger(AnomalyReport.class);

  private AnomalyReport() {}

  static void log(LocalDate session, SessionCloses result) {
    logEach(
        "Last trades share a timestamp with different prices, file order kept",
        session,
        result.ambiguousTimestamps());
    logEach(
        "Tracked instrument traded on another venue only, check its MIC",
        session,
        result.tradedElsewhereOnly());
    logEach("Close has more than 4 decimal places, not stored", session, result.notStorable());
    logEach(
        "Trades in another currency than the instrument, ignored", session, result.otherCurrency());
  }

  private static void logEach(String anomaly, LocalDate session, Set<Listing> listings) {
    for (Listing listing : listings) {
      String kind = LogSanitizer.oneLine(anomaly);
      String value = LogSanitizer.oneLine(listing);
      String day = LogSanitizer.oneLine(session);
      LOG.warn("{}: listing={}, session={}", kind, value, day);
    }
  }
}
