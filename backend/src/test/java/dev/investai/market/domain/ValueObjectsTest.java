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

  @Test
  void dailyCloseRefusesAPriceThatWouldBeRounded() {
    assertThatIllegalArgumentException()
        .isThrownBy(
            () ->
                new DailyClose(
                    InstrumentId.newId(),
                    LocalDate.parse("2026-10-16"),
                    new BigDecimal("1.23456"),
                    Currency.getInstance("EUR"),
                    PriceSource.EURONEXT));
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
    var eur = Currency.getInstance("EUR");
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
            Currency.getInstance("EUR")));

    assertThat(data.instruments()).isEmpty();
    assertThat(data.ignored()).isEqualTo(2);
    assertThat(List.copyOf(data.instruments())).isEmpty();
  }
}
