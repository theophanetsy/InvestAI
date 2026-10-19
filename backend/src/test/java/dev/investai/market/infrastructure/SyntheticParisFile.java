package dev.investai.market.infrastructure;

import static dev.investai.market.MarketTestData.EUR;
import static dev.investai.market.MarketTestData.isin;

import dev.investai.market.domain.InstrumentType;
import dev.investai.market.domain.ListedInstrument;
import dev.investai.market.domain.Listing;
import dev.investai.market.domain.Mic;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Synthetic Euronext Paris equities file of realistic size (spec 2.2, criterion 12): the real one
 * holds ~483,000 trades, ~85 MB once inflated. Built in memory as a ZIP, line by line, with the
 * expected volume and trade count of each listing. Never real market data.
 */
final class SyntheticParisFile {

  /** First trade time: 09:00 in Paris on Friday 16 October 2026. */
  private static final Instant OPEN = Instant.parse("2026-10-16T07:00:00Z");

  /** 60 ms between trades: 483,000 trades end before the close of the session. */
  private static final long STEP_NANOS = 60_000_000L;

  private static final String ORDER_BOOK = "1";
  private static final String OFF_BOOK = "3";
  private static final int OFF_BOOK_EVERY = 7;
  private static final int OTHER_VENUE_EVERY = 50;
  private static final int MAX_QUANTITY = 500;

  private final List<ListedInstrument> referenceData;
  private final byte[] archive;
  private final long inflatedBytes;
  private final Map<Listing, Long> volumes;
  private final Map<Listing, Integer> tradeCounts;

  private SyntheticParisFile(
      List<ListedInstrument> referenceData,
      byte[] archive,
      long inflatedBytes,
      Map<Listing, Long> volumes,
      Map<Listing, Integer> tradeCounts) {
    this.referenceData = referenceData;
    this.archive = archive;
    this.inflatedBytes = inflatedBytes;
    this.volumes = volumes;
    this.tradeCounts = tradeCounts;
  }

  /**
   * Generates a file.
   *
   * @param regulated listings of the regulated market ({@code XPAR}) in the reference data
   * @param growth listings of Euronext Growth ({@code ALXP}) in the reference data
   * @param unknown ISINs of the file missing from the reference data
   * @param trades number of trades
   * @return the file and what the collection must find in it
   */
  static SyntheticParisFile generate(int regulated, int growth, int unknown, int trades) {
    List<ListedInstrument> reference =
        IntStream.range(0, regulated + growth)
            .mapToObj(n -> listed(n, n < regulated ? Mic.XPAR : Mic.ALXP))
            .toList();
    List<Listing> listings =
        Stream.concat(
                reference.stream().map(ListedInstrument::listing),
                IntStream.range(0, unknown)
                    .mapToObj(n -> new Listing(isin(code("BE", n)), Mic.XPAR)))
            .toList();
    var bytes = new ByteArrayOutputStream();
    var volumes = new HashMap<Listing, Long>();
    var counts = new HashMap<Listing, Integer>();
    long inflated;
    try (var zip = new ZipOutputStream(bytes);
        Writer out = new OutputStreamWriter(zip, StandardCharsets.UTF_8)) {
      zip.putNextEntry(new ZipEntry("Trades_Equities_PAR.csv"));
      inflated = line(out, MifirFixtures.COPYRIGHT) + line(out, MifirFixtures.header());
      for (int i = 0; i < trades; i++) {
        inflated += line(out, trade(i, listings, reference.size(), volumes, counts));
      }
      out.flush();
      zip.closeEntry();
    } catch (IOException ex) {
      throw new UncheckedIOException(ex);
    }
    return new SyntheticParisFile(reference, bytes.toByteArray(), inflated, volumes, counts);
  }

  private static ListedInstrument listed(int n, Mic mic) {
    return new ListedInstrument(
        new Listing(isin(code("FR", n)), mic),
        "S" + n,
        "Synthetic " + n,
        InstrumentType.STOCK,
        EUR);
  }

  private static String code(String country, int n) {
    return String.format(Locale.ROOT, "%s%09d", country, n);
  }

  private static String trade(
      int i,
      List<Listing> listings,
      int known,
      Map<Listing, Long> volumes,
      Map<Listing, Integer> counts) {
    int index = i % listings.size();
    Listing listing = listings.get(index);
    // Some rounds on another venue, never every trade of a listing.
    boolean otherVenue = (i / listings.size() + index) % OTHER_VENUE_EVERY == 0;
    Mic venue = otherVenue ? other(listing.mic()) : listing.mic();
    String mechanism = i % OFF_BOOK_EVERY == 0 ? OFF_BOOK : ORDER_BOOK;
    int quantity = i % MAX_QUANTITY + 1;
    if (index < known && !otherVenue && ORDER_BOOK.equals(mechanism)) {
      volumes.merge(listing, (long) quantity, Long::sum);
      counts.merge(listing, 1, Integer::sum);
    }
    return MifirFixtures.trade(
        OPEN.plusNanos(i * STEP_NANOS).toString(),
        listing.isin().value(),
        (10 + index % 90) + "." + (10 + i % 90) + "00000",
        quantity + ".0",
        mechanism,
        venue.value());
  }

  private static Mic other(Mic mic) {
    return Mic.XPAR.equals(mic) ? Mic.ALXP : Mic.XPAR;
  }

  private static long line(Writer out, String line) throws IOException {
    out.write(line);
    out.write("\r\n");
    return line.length() + 2L;
  }

  List<ListedInstrument> referenceData() {
    return referenceData;
  }

  InputStream open() {
    return new ByteArrayInputStream(archive);
  }

  long compressedBytes() {
    return archive.length;
  }

  long inflatedBytes() {
    return inflatedBytes;
  }

  Map<Listing, Long> volumes() {
    return volumes;
  }

  Map<Listing, Integer> tradeCounts() {
    return tradeCounts;
  }
}
