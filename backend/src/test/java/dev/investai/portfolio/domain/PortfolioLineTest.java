package dev.investai.portfolio.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.investai.market.domain.InstrumentId;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Declared lines and cash (spec 2.3, criteria 3 and 4). */
class PortfolioLineTest {

  private static final Instant NOW = Instant.parse("2026-10-19T08:00:00Z");
  private static final InstrumentId INSTRUMENT = new InstrumentId(UUID.randomUUID());

  @Test
  void averagePriceIsKeptWithFourDecimalPlaces() {
    var line =
        new PortfolioLine(PortfolioLineId.newId(), INSTRUMENT, 3, new BigDecimal("12.5"), NOW);

    assertThat(line.averagePrice()).isEqualTo(new BigDecimal("12.5000"));
  }

  @ParameterizedTest
  @ValueSource(longs = {0, -1, 1_000_000_001L})
  void quantityIsAPositiveInteger(long quantity) {
    assertThatThrownBy(
            () -> new PortfolioLine(PortfolioLineId.newId(), INSTRUMENT, quantity, null, NOW))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @ParameterizedTest
  @ValueSource(strings = {"0", "-3", "1.00001"})
  void averagePriceIsPositiveWithAtMostFourDecimalPlaces(String price) {
    assertThatThrownBy(
            () ->
                new PortfolioLine(
                    PortfolioLineId.newId(), INSTRUMENT, 1, new BigDecimal(price), NOW))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void updateKeepsTheIdentityAndTheInstrument() {
    var line = new PortfolioLine(PortfolioLineId.newId(), INSTRUMENT, 3, null, NOW);

    var updated = line.update(5, BigDecimal.TEN, NOW.plusSeconds(60));

    assertThat(updated.id()).isEqualTo(line.id());
    assertThat(updated.instrumentId()).isEqualTo(INSTRUMENT);
    assertThat(updated.quantity()).isEqualTo(5);
    assertThat(updated.updatedAt()).isEqualTo(NOW.plusSeconds(60));
    assertThat(line.id().toString()).isEqualTo(line.id().value().toString());
  }

  @Test
  void cashIsNonNegativeAtTheCent() {
    assertThat(new Cash(new BigDecimal("12.3"), NOW).amount()).isEqualTo(new BigDecimal("12.30"));
    assertThat(new Cash(BigDecimal.ZERO, NOW).amount()).isEqualTo(new BigDecimal("0.00"));
    assertThatThrownBy(() -> new Cash(new BigDecimal("-0.01"), NOW))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new Cash(new BigDecimal("0.001"), NOW))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
