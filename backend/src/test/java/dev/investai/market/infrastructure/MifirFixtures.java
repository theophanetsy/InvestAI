package dev.investai.market.infrastructure;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Synthetic Euronext deferred trade files, in the real format (ADR-0009): a copyright line, an
 * unquoted header of the 20 MiFIR columns, then quoted values. Never real market data: publishing
 * Euronext files in the repository would be a redistribution.
 */
final class MifirFixtures {

  static final String COPYRIGHT =
      "(c) 2026 Synthetic test data. Not Euronext data, generated for InvestAI tests.";

  static final List<String> COLUMNS =
      List.of(
          "TradingDateTime",
          "PublicationDateTime",
          "MifidInstrumentID",
          "MifidPrice",
          "MifidQuantity",
          "MifidPriceNotation",
          "MifidCurrency",
          "MmtMarketMechanism",
          "MmtNegotiationIndicator",
          "MmtModificationIndicator",
          "MmtBenchMarkIndicator",
          "MmtContributionToPrice",
          "MmtAlgorithmicIndicator",
          "MmtPublicationMode",
          "Venue",
          "ThirdCountryTradingVenueExecution",
          "TradeUniqueIdentifier",
          "MissingPrice",
          "MmtContingentTransactionIndicator",
          "VenueOfPublication");

  private MifirFixtures() {}

  /**
   * One quoted line, as Euronext publishes it.
   *
   * @param time {@code TradingDateTime}, e.g. {@code 2026-10-16T15:35:24.250725Z}
   * @param isin {@code MifidInstrumentID}
   * @param price {@code MifidPrice}, e.g. {@code 173.1600000}
   * @param mechanism {@code MmtMarketMechanism}
   * @param venue {@code Venue}
   */
  static String trade(String time, String isin, String price, String mechanism, String venue) {
    List<String> values =
        List.of(
            time,
            time,
            isin,
            price,
            "10.0",
            "MONE",
            "EUR",
            mechanism,
            "-",
            "-",
            "-",
            "-",
            "-",
            "-",
            venue,
            "",
            "SYNTH" + Integer.toHexString((time + isin + price).hashCode()),
            "",
            "-",
            venue);
    return quoted(values);
  }

  static String quoted(List<String> values) {
    return "\"" + String.join("\",\"", values) + "\"";
  }

  static String header() {
    return String.join(",", COLUMNS);
  }

  static String csv(String... trades) {
    List<String> lines = new ArrayList<>();
    lines.add(COPYRIGHT);
    lines.add(header());
    Collections.addAll(lines, trades);
    return String.join("\r\n", lines) + "\r\n";
  }

  static byte[] zip(String csv) {
    return zip(new Entry("Trades_Equities.csv", csv));
  }

  static byte[] zip(Entry... entries) {
    var bytes = new ByteArrayOutputStream();
    try (var zip = new ZipOutputStream(bytes)) {
      for (Entry entry : entries) {
        write(zip, entry);
      }
    } catch (IOException ex) {
      throw new UncheckedIOException(ex);
    }
    return bytes.toByteArray();
  }

  private static void write(ZipOutputStream zip, Entry entry) throws IOException {
    zip.putNextEntry(new ZipEntry(entry.name()));
    zip.write(entry.content().getBytes(StandardCharsets.UTF_8));
    zip.closeEntry();
  }

  record Entry(String name, String content) {}
}
