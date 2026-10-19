package dev.investai.market.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class PriceRangeTest {

  private static final LocalDate TODAY = LocalDate.parse("2026-10-19");

  @Test
  void defaultsToTheLastYear() {
    assertThat(PriceRange.resolve(null, null, TODAY))
        .isEqualTo(new PriceRange(LocalDate.parse("2025-10-19"), TODAY));
  }

  @Test
  void defaultStartIsOneYearBeforeTheGivenEnd() {
    assertThat(PriceRange.resolve(null, LocalDate.parse("2026-01-31"), TODAY))
        .isEqualTo(new PriceRange(LocalDate.parse("2025-01-31"), LocalDate.parse("2026-01-31")));
  }

  @Test
  void defaultEndIsToday() {
    assertThat(PriceRange.resolve(LocalDate.parse("2026-10-01"), null, TODAY))
        .isEqualTo(new PriceRange(LocalDate.parse("2026-10-01"), TODAY));
  }

  @Test
  void singleDayAndFiveYearsAreAccepted() {
    assertThat(PriceRange.resolve(TODAY, TODAY, TODAY)).isEqualTo(new PriceRange(TODAY, TODAY));
    assertThat(PriceRange.resolve(LocalDate.parse("2021-10-19"), null, TODAY).from())
        .isEqualTo(LocalDate.parse("2021-10-19"));
  }

  @Test
  void startAfterEndIsRejected() {
    assertThatThrownBy(
            () ->
                PriceRange.resolve(
                    LocalDate.parse("2026-10-02"), LocalDate.parse("2026-10-01"), TODAY))
        .isInstanceOfSatisfying(
            InvalidPriceRangeException.class,
            ex -> {
              assertThat(ex.parameter()).isEqualTo("from");
              assertThat(ex.constraint()).isEqualTo(InvalidPriceRangeException.NOT_AFTER_TO);
            });
  }

  @Test
  void moreThanFiveYearsIsRejected() {
    assertThatThrownBy(() -> PriceRange.resolve(LocalDate.parse("2021-10-18"), null, TODAY))
        .isInstanceOfSatisfying(
            InvalidPriceRangeException.class,
            ex -> assertThat(ex.constraint()).isEqualTo(InvalidPriceRangeException.MAX_FIVE_YEARS));
  }
}
