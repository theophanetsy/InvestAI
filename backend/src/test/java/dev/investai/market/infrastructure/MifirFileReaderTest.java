package dev.investai.market.infrastructure;

import static dev.investai.market.infrastructure.MifirFixtures.csv;
import static dev.investai.market.infrastructure.MifirFixtures.trade;
import static dev.investai.market.infrastructure.MifirFixtures.zip;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.investai.market.domain.Isin;
import dev.investai.market.domain.MarketDataUnavailableException;
import dev.investai.market.domain.Mic;
import dev.investai.market.domain.MifirTrade;
import dev.investai.market.domain.TradeFileSource;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Currency;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class MifirFileReaderTest {

  private static final String AIR_LIQUIDE = "FR0000120073";
  private static final String SESSION_TIME = "2026-10-16T15:35:24.250725Z";
  private static final long LIMIT = 1_000_000;

  private final MifirFileReader reader = new MifirFileReader(LIMIT);
  private final List<MifirTrade> trades = new ArrayList<>();

  @Test
  void readsTradesInFileOrderWithExactPricesAndUtcTimes() throws IOException {
    var summary =
        read(
            zip(
                csv(
                    trade(SESSION_TIME, AIR_LIQUIDE, "173.1600000", "1", "XPAR"),
                    trade(
                        "2026-10-16T07:00:00.116443Z",
                        "FR0000120271",
                        "55.1200000",
                        "3",
                        "XPAR"))));

    assertThat(trades)
        .containsExactly(
            new MifirTrade(
                new Isin(AIR_LIQUIDE),
                Instant.parse(SESSION_TIME),
                new BigDecimal("173.1600000"),
                Currency.getInstance("EUR"),
                1,
                Mic.XPAR),
            new MifirTrade(
                new Isin("FR0000120271"),
                Instant.parse("2026-10-16T07:00:00.116443Z"),
                new BigDecimal("55.1200000"),
                Currency.getInstance("EUR"),
                3,
                Mic.XPAR));
    assertThat(summary).isEqualTo(new TradeFileSource.Summary(LocalDate.parse("2026-10-16"), 2, 0));
  }

  @Test
  void columnsAreFoundByNameWhateverTheirOrder() throws IOException {
    String content =
        String.join(
            "\n",
            "copyright",
            "Venue,MifidCurrency,MmtMarketMechanism,MifidPrice,MifidInstrumentID,TradingDateTime",
            "\"ALXP\",\"EUR\",\"1\",\"181.0000000\",\"FR0000033888\",\"" + SESSION_TIME + "\"");

    read(zip(content));

    assertThat(trades)
        .singleElement()
        .satisfies(trade -> assertThat(trade.venue()).isEqualTo(Mic.ALXP));
  }

  @Test
  void malformedLinesAreSkippedAndCounted() throws IOException {
    var summary =
        read(
            zip(
                csv(
                    trade(SESSION_TIME, "FR0000120074", "1.0", "1", "XPAR"), // bad check digit
                    trade(SESSION_TIME, AIR_LIQUIDE, "abc", "1", "XPAR"),
                    trade("16/10/2026 15:35", AIR_LIQUIDE, "1.0", "1", "XPAR"),
                    trade(SESSION_TIME, AIR_LIQUIDE, "1.0", "x", "XPAR"),
                    trade(SESSION_TIME, AIR_LIQUIDE, "1.0", "1", "xpar"),
                    "\"too\",\"few\",\"fields\"",
                    "\"unterminated",
                    "",
                    trade(SESSION_TIME, AIR_LIQUIDE, "173.1600000", "1", "XPAR"))));

    assertThat(trades).hasSize(1);
    assertThat(summary.trades()).isEqualTo(1);
    assertThat(summary.rejected()).isEqualTo(7);
  }

  @Test
  void sessionIsTheLatestTradingDateInParis() throws IOException {
    var summary =
        read(
            zip(
                csv(
                    trade("2026-10-15T15:00:00Z", AIR_LIQUIDE, "1.0", "1", "XPAR"),
                    trade("2026-10-16T08:00:00Z", AIR_LIQUIDE, "1.0", "1", "XPAR"))));

    assertThat(summary.session()).isEqualTo(LocalDate.parse("2026-10-16"));
  }

  @Test
  void fileWithoutTradeHasNoSession() throws IOException {
    assertThat(read(zip(csv())).session()).isNull();
  }

  @ParameterizedTest
  @ValueSource(strings = {"MifidPrice", "Venue", "MmtMarketMechanism", "TradingDateTime"})
  void missingRequiredColumnFailsExplicitly(String column) {
    String header =
        MifirFixtures.COLUMNS.stream()
            .filter(name -> !name.equals(column))
            .collect(Collectors.joining(","));

    assertThatThrownBy(() -> read(zip("copyright\n" + header + "\n")))
        .isInstanceOf(MarketDataUnavailableException.class)
        .hasMessageContaining("missing column " + column);
  }

  @Test
  void fileWithoutHeaderFails() {
    assertThatThrownBy(() -> read(zip("copyright only")))
        .isInstanceOf(MarketDataUnavailableException.class)
        .hasMessageContaining("no header");
  }

  @Test
  void archiveWithoutEntryFails() {
    assertThatThrownBy(() -> read(MifirFixtures.zip(new MifirFixtures.Entry[0])))
        .isInstanceOf(MarketDataUnavailableException.class)
        .hasMessageContaining("empty archive");
  }

  @Test
  void archiveWithAnotherKindOfEntryFails() {
    assertThatThrownBy(() -> read(zip(new MifirFixtures.Entry("trades.exe", csv()))))
        .isInstanceOf(MarketDataUnavailableException.class)
        .hasMessageContaining("unexpected entry");
  }

  @Test
  void archiveWithSeveralEntriesFails() {
    byte[] archive =
        zip(new MifirFixtures.Entry("a.csv", csv()), new MifirFixtures.Entry("b.csv", csv()));

    assertThatThrownBy(() -> read(archive))
        .isInstanceOf(MarketDataUnavailableException.class)
        .hasMessageContaining("more than one entry");
  }

  @Test
  void uncompressedSizeIsCapped() {
    String line = trade(SESSION_TIME, AIR_LIQUIDE, "173.1600000", "1", "XPAR");
    String[] lines = IntStream.range(0, 10_000).mapToObj(i -> line).toArray(String[]::new);
    // Highly compressible: a few kilobytes on the wire, ~2 MB once inflated.
    byte[] archive = zip(csv(lines));
    assertThat(archive.length).isLessThan((int) LIMIT);

    assertThatThrownBy(() -> read(archive))
        .isInstanceOf(MarketDataUnavailableException.class)
        .hasMessageContaining("uncompressed size limit");
  }

  @Test
  void notAZipFails() {
    assertThatThrownBy(() -> read("not a zip".getBytes(StandardCharsets.UTF_8)))
        .isInstanceOf(MarketDataUnavailableException.class);
  }

  private TradeFileSource.Summary read(byte[] archive) throws IOException {
    try (InputStream in = new ByteArrayInputStream(archive)) {
      return reader.read("EQUITIES", in, trades::add);
    }
  }
}
