package dev.investai.market.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import dev.investai.market.domain.InstrumentType;
import dev.investai.market.domain.Isin;
import dev.investai.market.domain.ListedInstrument;
import dev.investai.market.domain.Listing;
import dev.investai.market.domain.Mic;
import dev.investai.market.domain.ReferenceData;
import java.util.Currency;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

/**
 * MIC and type rules on the ADR-0009 panel, as EODHD lists them for Paris (synthetic entries in the
 * {@code exchange-symbol-list} format). ASML, the seventh value of the panel, is listed in
 * Amsterdam: other venues are out of scope (spec 2.1) and never appear in the Paris list.
 */
class EodhdSymbolsTest {

  private static final String COMMON_STOCK = "Common Stock";
  private static final String AI_ISIN = "FR0000120073";
  private static final String AIR_LIQUIDE = "Air Liquide";
  private static final String EURO = "EUR";

  private static final Currency EUR = Currency.getInstance(EURO);

  @Test
  void parisPanelGetsItsVenueAndType() {
    ReferenceData data =
        EodhdSymbols.toReferenceData(
            List.of(
                symbol("AI", AIR_LIQUIDE, COMMON_STOCK, AI_ISIN),
                symbol("TTE", "TotalEnergies SE", COMMON_STOCK, "FR0000120271"),
                symbol("ALGEV", "Gevelot", COMMON_STOCK, "FR0000033888"),
                symbol("CW8", "Amundi MSCI World Swap UCITS ETF", "ETF", "LU1681043599"),
                symbol("WPEA", "iShares MSCI World Swap PEA UCITS ETF", "ETF", "IE0002XZSHO1"),
                symbol("CAC", "Amundi CAC 40 UCITS ETF Dist", "ETF", "FR0007052782")));

    assertThat(data.ignored()).isZero();
    assertThat(data.instruments())
        .extracting(i -> i.ticker() + " " + i.listing() + " " + i.type())
        .containsExactly(
            "AI FR0000120073@XPAR STOCK",
            "TTE FR0000120271@XPAR STOCK",
            "ALGEV FR0000033888@ALXP STOCK",
            "CW8 LU1681043599@XPAR ETF",
            "WPEA IE0002XZSHO1@XPAR ETF",
            "CAC FR0007052782@XPAR ETF");
    assertThat(data.instruments().getFirst())
        .isEqualTo(
            new ListedInstrument(
                new Listing(new Isin(AI_ISIN), Mic.XPAR),
                "AI",
                AIR_LIQUIDE,
                InstrumentType.STOCK,
                EUR));
  }

  @Test
  void otherTypesAreMapped() {
    ReferenceData data =
        EodhdSymbols.toReferenceData(
            List.of(
                symbol("FD", "Some fund", "FUND", AI_ISIN),
                symbol("PS", "Preferred", "Preferred Stock", "FR0000120271")));

    assertThat(data.instruments())
        .extracting(ListedInstrument::type)
        .containsExactly(InstrumentType.FUND, InstrumentType.OTHER);
  }

  @Test
  void entriesWithoutUsableIsinCodeNameOrCurrencyAreIgnoredAndCounted() {
    ReferenceData data =
        EodhdSymbols.toReferenceData(
            List.of(
                symbol("NOISIN", "No ISIN", COMMON_STOCK, null),
                symbol("EMPTY", "Empty ISIN", COMMON_STOCK, ""),
                symbol("BAD", "Bad check digit", COMMON_STOCK, "FR0000120074"),
                new EodhdSymbols.Symbol(null, "No code", COMMON_STOCK, EURO, AI_ISIN),
                new EodhdSymbols.Symbol("NONAME", " ", COMMON_STOCK, EURO, AI_ISIN),
                new EodhdSymbols.Symbol("CUR", "Bad currency", COMMON_STOCK, "EURO", AI_ISIN),
                new EodhdSymbols.Symbol("X".repeat(40), "Long code", COMMON_STOCK, EURO, AI_ISIN),
                symbol("AI", AIR_LIQUIDE, COMMON_STOCK, AI_ISIN)));

    assertThat(data.ignored()).isEqualTo(7);
    assertThat(data.instruments()).extracting(ListedInstrument::ticker).containsExactly("AI");
  }

  @Test
  void duplicateListingsKeepTheFirstEntry() {
    ReferenceData data =
        EodhdSymbols.toReferenceData(
            List.of(
                symbol("AI", AIR_LIQUIDE, COMMON_STOCK, AI_ISIN),
                symbol("AI2", "Air Liquide bis", COMMON_STOCK, AI_ISIN)));

    assertThat(data.instruments()).extracting(ListedInstrument::ticker).containsExactly("AI");
    assertThat(data.ignored()).isEqualTo(1);
  }

  @Test
  void longNamesAreTruncatedToTheColumnSize() {
    ReferenceData data =
        EodhdSymbols.toReferenceData(List.of(symbol("AI", "N".repeat(300), COMMON_STOCK, AI_ISIN)));

    assertThat(data.instruments().getFirst().name()).hasSize(256);
  }

  @Test
  void symbolOfAParisInstrument() {
    assertThat(EodhdSymbols.symbolOf("ALGEV", Mic.ALXP)).hasValue("ALGEV.PA");
    assertThat(EodhdSymbols.symbolOf("AI", Mic.XPAR)).hasValue("AI.PA");
    assertThat(EodhdSymbols.symbolOf("ASML", new Mic("XAMS"))).isEmpty();
  }

  private static EodhdSymbols.Symbol symbol(
      String code, String name, String type, @Nullable String isin) {
    return new EodhdSymbols.Symbol(code, name, type, EURO, isin);
  }
}
