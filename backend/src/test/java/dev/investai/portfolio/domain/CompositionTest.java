package dev.investai.portfolio.domain;

import static org.assertj.core.api.Assertions.assertThat;

import dev.investai.market.domain.Indicator;
import dev.investai.market.domain.InstrumentId;
import dev.investai.market.domain.InstrumentType;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

/** Composition of the declared PEA (spec 2.4, criterion 7), computed by hand. */
class CompositionTest {

  private static final Instant NOW = Instant.parse("2026-10-19T08:00:00Z");
  private static final LocalDate FRIDAY = LocalDate.parse("2026-10-16");

  private final InstrumentId share = InstrumentId.newId();
  private final InstrumentId etf = InstrumentId.newId();
  private final InstrumentId unpriced = InstrumentId.newId();
  private final Map<InstrumentId, InstrumentType> types =
      Map.of(share, InstrumentType.STOCK, etf, InstrumentType.ETF, unpriced, InstrumentType.STOCK);

  private static PricedLine line(InstrumentId instrument, long quantity, @Nullable String close) {
    return new PricedLine(
        new PortfolioLine(PortfolioLineId.newId(), instrument, quantity, null, NOW),
        close == null ? null : new Quote(new BigDecimal(close), FRIDAY));
  }

  @Test
  void sharesConcentrationAndLargestLine() {
    // Share 10 × 173.16 = 1,731.60; ETF 2 × 500 = 1,000.00; cash 268.40; total 3,000.00.
    // Weights: 57.72 %, 33.33 %, cash 8.95 % (268.40 / 3,000 = 8.9467 %).
    // Herfindahl on the invested 2,731.60: (1,731.60² + 1,000²) / 2,731.60² = 0.535866 → 0.5359.
    var valuation =
        Valuation.of(
            List.of(line(share, 10, "173.16"), line(etf, 2, "500"), line(unpriced, 5, null)),
            new Cash(new BigDecimal("268.40"), NOW));

    Composition composition = Composition.of(valuation, types);

    assertThat(composition.lineCount()).isEqualTo(3);
    assertThat(composition.largestLinePercent()).isEqualTo(of("57.72"));
    assertThat(composition.herfindahlIndex()).isEqualTo(of("0.5359"));
    assertThat(composition.cashPercent()).isEqualTo(of("8.95"));
    assertThat(composition.etfPercent()).isEqualTo(of("33.33"));
    assertThat(composition.stockPercent()).isEqualTo(of("57.72"));
    assertThat(composition.otherPercent()).isEqualTo(of("0.00"));
  }

  @Test
  void aSingleLineIsFullyConcentrated() {
    var valuation = Valuation.of(List.of(line(etf, 3, "100")), null);

    Composition composition = Composition.of(valuation, types);

    assertThat(composition.herfindahlIndex()).isEqualTo(of("1.0000"));
    assertThat(composition.largestLinePercent()).isEqualTo(of("100.00"));
    assertThat(composition.cashPercent())
        .as("cash not declared")
        .isEqualTo(Indicator.NOT_COMPUTABLE);
  }

  @Test
  void anEmptyPortfolioHasNoShares() {
    Composition composition = Composition.of(Valuation.of(List.of(), null), types);

    assertThat(composition.lineCount()).isZero();
    assertThat(composition.largestLinePercent()).isEqualTo(Indicator.NOT_COMPUTABLE);
    assertThat(composition.herfindahlIndex()).isEqualTo(Indicator.NOT_COMPUTABLE);
    assertThat(composition.etfPercent()).isEqualTo(Indicator.NOT_COMPUTABLE);
    assertThat(composition.stockPercent()).isEqualTo(Indicator.NOT_COMPUTABLE);
  }

  private static Indicator of(String value) {
    return Indicator.of(new BigDecimal(value));
  }
}
