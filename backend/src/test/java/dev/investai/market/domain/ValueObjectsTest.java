package dev.investai.market.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Currency;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class ValueObjectsTest {

  private static final Currency EUR = Currency.getInstance("EUR");
  private static final LocalDate SESSION = LocalDate.parse("2026-10-16");

  @ParameterizedTest
  @CsvSource({
    "709.5500000, 709.5500",
    "0.0001, 0.0001",
    "100, 100.0000",
    "1E+2, 100.0000",
    "999999999999999.9999, 999999999999999.9999"
  })
  void pricesAreBroughtToScaleFourWithoutRounding(String raw, String expected) {
    assertThat(Prices.exact(new BigDecimal(raw)))
        .hasValueSatisfying(price -> assertThat(price).hasToString(expected));
  }

  @ParameterizedTest
  @ValueSource(strings = {"157.30909", "0.17505", "0", "-1", "1000000000000000"})
  void pricesThatCannotBeStoredExactlyAreRejected(String raw) {
    assertThat(Prices.exact(new BigDecimal(raw))).isEmpty();
  }

  @ParameterizedTest
  @ValueSource(strings = {"1E+2147483647", "12345678E+2147483640", "1E+16"})
  void pricesWithAHugeExponentAreRejectedWithoutFailing(String raw) {
    assertThat(Prices.exact(new BigDecimal(raw))).isEmpty();
  }

  @Test
  void dailyCloseRefusesAPriceThatWouldBeRounded() {
    assertThatIllegalArgumentException()
        .isThrownBy(
            () ->
                new DailyClose(
                    InstrumentId.newId(),
                    SESSION,
                    new BigDecimal("1.23456"),
                    EUR,
                    PriceSource.EURONEXT));
  }

  @ParameterizedTest
  @CsvSource({"-1, 3", "10, -1"})
  void dailyCloseRefusesANegativeActivity(Long volume, Integer tradeCount) {
    assertThatIllegalArgumentException()
        .isThrownBy(
            () ->
                new DailyClose(
                    InstrumentId.newId(),
                    SESSION,
                    BigDecimal.TEN,
                    EUR,
                    PriceSource.EURONEXT,
                    volume,
                    tradeCount));
  }

  @Test
  void eodhdCloseHasNoTradeCount() {
    assertThatIllegalArgumentException()
        .isThrownBy(
            () ->
                new DailyClose(
                    InstrumentId.newId(), SESSION, BigDecimal.TEN, EUR, PriceSource.EODHD, 10L, 3));
  }

  @Test
  void dailyCloseWithoutActivityHasAnUnknownVolumeAndTradeCount() {
    DailyClose close =
        new DailyClose(InstrumentId.newId(), SESSION, BigDecimal.TEN, EUR, PriceSource.EODHD);

    assertThat(close.volume()).isNull();
    assertThat(close.tradeCount()).isNull();
  }

  @ParameterizedTest
  @CsvSource({"10.0, 10", "0, 0", "1E+3, 1000", "9223372036854775807, 9223372036854775807"})
  void volumesAreWholeNumbersOfUnits(String raw, long expected) {
    assertThat(Volumes.units(new BigDecimal(raw))).contains(expected);
  }

  @ParameterizedTest
  @ValueSource(strings = {"-1", "2.5", "0.0001", "9223372036854775808"})
  void volumesThatAreNotWholeNonNegativeLongsAreUnknown(String raw) {
    assertThat(Volumes.units(new BigDecimal(raw))).isEmpty();
  }

  @Test
  void missingVolumeIsUnknown() {
    assertThat(Volumes.units(null)).isEmpty();
  }

  @ParameterizedTest
  @ValueSource(strings = {"xpar", "XPA", "XPARS", "XP-R"})
  void micIsFourUpperCaseCharacters(String value) {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new Mic(value))
        .withMessage("Invalid MIC");
  }

  @Test
  void identifiersPrintTheirValue() {
    var listing = new Listing(new Isin("FR0000120073"), Mic.XPAR);
    var id = InstrumentId.newId();

    assertThat(listing).hasToString("FR0000120073@XPAR");
    assertThat(id).hasToString(id.value().toString());
    assertThat(InstrumentId.newId()).isNotEqualTo(id);
  }

  @Test
  void instrumentIsTrackedOnceItHasATrackingDate() {
    var listing = new Listing(new Isin("FR0000120073"), Mic.XPAR);
    var eur = EUR;
    var id = InstrumentId.newId();

    assertThat(
            new Instrument(id, listing, "AI", "Air Liquide", InstrumentType.STOCK, eur, null, false)
                .tracked())
        .isFalse();
    assertThat(
            new Instrument(
                    id,
                    listing,
                    "AI",
                    "Air Liquide",
                    InstrumentType.STOCK,
                    eur,
                    Instant.EPOCH,
                    false)
                .tracked())
        .isTrue();
  }

  @Test
  void referenceDataIsImmutable() {
    var instruments = new ArrayList<ListedInstrument>();
    var data = new ReferenceData(instruments, 2);
    instruments.add(
        new ListedInstrument(
            new Listing(new Isin("FR0000120073"), Mic.XPAR),
            "AI",
            "Air Liquide",
            InstrumentType.STOCK,
            EUR));

    assertThat(data.instruments()).isEmpty();
    assertThat(data.ignored()).isEqualTo(2);
    assertThat(List.copyOf(data.instruments())).isEmpty();
  }
}
