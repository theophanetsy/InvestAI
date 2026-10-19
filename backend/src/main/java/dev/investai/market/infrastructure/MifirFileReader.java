package dev.investai.market.infrastructure;

import dev.investai.market.domain.Isin;
import dev.investai.market.domain.MarketDataUnavailableException;
import dev.investai.market.domain.Mic;
import dev.investai.market.domain.MifirTrade;
import dev.investai.market.domain.SessionCloseCalculator;
import dev.investai.market.domain.TradeFileSource;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Currency;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.jspecify.annotations.Nullable;

/**
 * Reads a Euronext deferred trade file (MiFIR) as a stream: a ZIP holding a single CSV whose first
 * line is a copyright notice, followed by a header. Columns are located by name; malformed lines
 * are skipped and counted. Nothing is written to disk and the file is never fully in memory.
 */
final class MifirFileReader {

  private static final String CSV_EXTENSION = ".csv";

  /** A MiFIR line is about 250 characters, the copyright notice about 520. */
  private static final int MAX_LINE_LENGTH = 16_384;

  private final long maxUncompressedBytes;

  /**
   * Creates a reader.
   *
   * @param maxUncompressedBytes inflated size above which reading fails
   */
  MifirFileReader(long maxUncompressedBytes) {
    this.maxUncompressedBytes = maxUncompressedBytes;
  }

  /**
   * Reads a whole archive.
   *
   * @param label name of the file in messages (segment)
   * @param archive the ZIP stream, e.g. an HTTP response body
   * @param trades receives each valid trade, in file order
   * @return the summary of the file
   * @throws MarketDataUnavailableException when the archive or the CSV is not usable
   */
  TradeFileSource.Summary read(String label, InputStream archive, Consumer<MifirTrade> trades) {
    try (ZipInputStream zip = new ZipInputStream(archive, StandardCharsets.UTF_8)) {
      return readArchive(label, zip, trades);
    } catch (BoundedInputStream.LimitExceededException ex) {
      throw failure(label, "uncompressed size limit exceeded", ex);
    } catch (BoundedLineReader.LineTooLongException ex) {
      throw failure(label, "line too long", ex);
    } catch (IOException ex) {
      throw failure(label, "read failed (" + ex.getClass().getSimpleName() + ")", ex);
    }
  }

  private TradeFileSource.Summary readArchive(
      String label, ZipInputStream zip, Consumer<MifirTrade> trades) throws IOException {
    ZipEntry entry = zip.getNextEntry();
    if (entry == null) {
      throw failure(label, "empty archive");
    }
    if (!entry.getName().toLowerCase(Locale.ROOT).endsWith(CSV_EXTENSION)) {
      throw failure(label, "unexpected entry in archive");
    }
    TradeFileSource.Summary summary =
        readCsv(label, new BoundedInputStream(zip, maxUncompressedBytes), trades);
    if (zip.getNextEntry() != null) {
      throw failure(label, "more than one entry in archive");
    }
    return summary;
  }

  private TradeFileSource.Summary readCsv(
      String label, InputStream csv, Consumer<MifirTrade> trades) throws IOException {
    // Not closed here: closing would close the ZIP stream before its next entry is checked.
    BoundedLineReader lines =
        new BoundedLineReader(new InputStreamReader(csv, StandardCharsets.UTF_8), MAX_LINE_LENGTH);
    lines.readLine(); // copyright notice
    String header = lines.readLine();
    if (header == null) {
      throw failure(label, "no header");
    }
    Columns columns = Columns.locate(label, CsvLine.parse(header).orElseGet(() -> new String[0]));
    Counter counter = new Counter();
    for (String line = lines.readLine(); line != null; line = lines.readLine()) {
      if (!line.isBlank()) {
        parse(line, columns)
            .ifPresentOrElse(trade -> counter.accept(trade, trades), counter::reject);
      }
    }
    return counter.summary();
  }

  private static Optional<MifirTrade> parse(String line, Columns columns) {
    Optional<String[]> fields = CsvLine.parse(line).filter(columns::fits);
    if (fields.isEmpty()) {
      return Optional.empty();
    }
    try {
      return Optional.of(columns.trade(fields.get()));
    } catch (IllegalArgumentException | DateTimeParseException ex) {
      // NumberFormatException is an IllegalArgumentException; so are invalid ISIN, MIC, currency.
      return Optional.empty();
    }
  }

  private static MarketDataUnavailableException failure(String label, String reason) {
    return new MarketDataUnavailableException("Trade file " + label + ": " + reason);
  }

  private static MarketDataUnavailableException failure(
      String label, String reason, IOException cause) {
    return new MarketDataUnavailableException(
        "Trade file " + label + ": " + reason, RedactedCause.of(cause));
  }

  /** Counts lines and tracks the latest session of the file. */
  private static final class Counter {
    private long valid;
    private long rejected;
    private @Nullable LocalDate session;

    void accept(MifirTrade trade, Consumer<MifirTrade> trades) {
      valid++;
      LocalDate tradeSession = SessionCloseCalculator.sessionOf(trade);
      if (session == null || tradeSession.isAfter(session)) {
        session = tradeSession;
      }
      trades.accept(trade);
    }

    void reject() {
      rejected++;
    }

    TradeFileSource.Summary summary() {
      return new TradeFileSource.Summary(session, valid, rejected);
    }
  }

  /** Required columns, by header name. */
  private enum Column {
    TRADING_TIME("TradingDateTime"),
    ISIN("MifidInstrumentID"),
    PRICE("MifidPrice"),
    CURRENCY("MifidCurrency"),
    MECHANISM("MmtMarketMechanism"),
    VENUE("Venue");

    private final String header;

    Column(String header) {
      this.header = header;
    }
  }

  /** Positions of the required columns in the header. */
  private static final class Columns {
    private final int width;
    private final Map<Column, Integer> positions;

    private Columns(int width, Map<Column, Integer> positions) {
      this.width = width;
      this.positions = positions;
    }

    static Columns locate(String label, String... header) {
      List<String> names = List.of(header);
      Map<Column, Integer> positions = new EnumMap<>(Column.class);
      for (Column column : Column.values()) {
        int position = names.indexOf(column.header);
        positions.put(column, position);
        if (position < 0) {
          throw failure(label, "missing column " + column.header);
        }
      }
      return new Columns(header.length, positions);
    }

    boolean fits(String... values) {
      return values.length == width;
    }

    MifirTrade trade(String... values) {
      return new MifirTrade(
          new Isin(value(values, Column.ISIN)),
          Instant.parse(value(values, Column.TRADING_TIME)),
          new BigDecimal(value(values, Column.PRICE)),
          Currency.getInstance(value(values, Column.CURRENCY)),
          Integer.parseInt(value(values, Column.MECHANISM)),
          new Mic(value(values, Column.VENUE)));
    }

    private String value(String[] values, Column column) {
      return values[Objects.requireNonNull(positions.get(column))];
    }
  }
}
