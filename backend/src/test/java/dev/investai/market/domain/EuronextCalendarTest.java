package dev.investai.market.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class EuronextCalendarTest {

  private static final LocalDate MAUNDY_THURSDAY = LocalDate.parse("2026-04-02");
  private static final LocalDate TUESDAY_AFTER_EASTER = LocalDate.parse("2026-04-07");

  private final EuronextCalendar calendar = new EuronextCalendar();

  /** Euronext closing days 2025-2027 (weekdays only). */
  @ParameterizedTest
  @ValueSource(
      strings = {
        "2025-01-01",
        "2025-04-18",
        "2025-04-21",
        "2025-05-01",
        "2025-12-25",
        "2025-12-26",
        "2026-01-01",
        "2026-04-03",
        "2026-04-06",
        "2026-05-01",
        "2026-12-25",
        "2027-01-01",
        "2027-03-26",
        "2027-03-29"
      })
  void holidaysAreClosed(String date) {
    assertThat(calendar.isTradingDay(LocalDate.parse(date))).isFalse();
  }

  @ParameterizedTest
  @ValueSource(strings = {"2025-04-19", "2026-10-17", "2026-10-18", "2027-05-01", "2027-12-25"})
  void weekendsAreClosed(String date) {
    assertThat(calendar.isTradingDay(LocalDate.parse(date))).isFalse();
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "2025-04-17", "2025-04-22", "2025-12-24", "2025-12-31", "2026-04-02", "2026-04-07",
        "2026-05-08", "2026-07-14", "2026-10-16", "2026-12-28", "2027-03-25", "2027-03-30"
      })
  void otherWeekdaysAreTradingDays(String date) {
    assertThat(calendar.isTradingDay(LocalDate.parse(date))).isTrue();
  }

  @Test
  void eachYearHasTheExpectedNumberOfSessions() {
    // 2027: 1 May and 25 December fall on a Saturday, 26 December on a Sunday.
    assertThat(sessionsIn(2025)).hasSize(255);
    assertThat(sessionsIn(2026)).hasSize(256);
    assertThat(sessionsIn(2027)).hasSize(258);
  }

  @ParameterizedTest
  @CsvSource({
    "2026-10-19, 2026-10-16", // Monday -> Friday
    "2026-10-17, 2026-10-16", // Saturday -> Friday
    "2026-10-16, 2026-10-15",
    "2026-04-07, 2026-04-02", // Tuesday after Easter Monday -> Thursday before Good Friday
    "2027-01-04, 2026-12-31",
    "2025-12-29, 2025-12-24"
  })
  void previousTradingDay(String date, String expected) {
    assertThat(calendar.previousTradingDay(LocalDate.parse(date)))
        .isEqualTo(LocalDate.parse(expected));
  }

  @Test
  void tradingDaysBetweenIsInclusiveAndSorted() {
    assertThat(
            calendar.tradingDaysBetween(
                LocalDate.parse("2026-04-01"), LocalDate.parse("2026-04-08")))
        .containsExactly(
            LocalDate.parse("2026-04-01"),
            MAUNDY_THURSDAY,
            TUESDAY_AFTER_EASTER,
            LocalDate.parse("2026-04-08"));
  }

  @Test
  void tradingDaysBetweenIsEmptyForAnEmptyOrClosedRange() {
    assertThat(
            calendar.tradingDaysBetween(
                LocalDate.parse("2026-10-17"), LocalDate.parse("2026-10-18")))
        .isEmpty();
    assertThat(
            calendar.tradingDaysBetween(
                LocalDate.parse("2026-10-16"), LocalDate.parse("2026-10-15")))
        .isEmpty();
  }

  @Test
  void rejectsDatesOutsideTheSupportedRange() {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> calendar.isTradingDay(LocalDate.of(1582, 1, 3)));
  }

  @Test
  void neverTradesOnWeekends() {
    assertThat(sessionsIn(2026))
        .noneMatch(
            day ->
                day.getDayOfWeek() == DayOfWeek.SATURDAY || day.getDayOfWeek() == DayOfWeek.SUNDAY);
  }

  @Test
  void listsTheLastSessionsSkippingHolidaysAndEndingBeforeANonTradingDay() {
    // Easter 2026: Good Friday 3 April and Easter Monday 6 April are holidays. Ending on Monday
    // 6 April starts from Thursday 2 April.
    assertThat(calendar.sessionsEnding(LocalDate.parse("2026-04-06"), 3))
        .containsExactly(
            LocalDate.parse("2026-03-31"), LocalDate.parse("2026-04-01"), MAUNDY_THURSDAY);
    assertThat(calendar.sessionsEnding(TUESDAY_AFTER_EASTER, 2))
        .containsExactly(MAUNDY_THURSDAY, TUESDAY_AFTER_EASTER);
    assertThatIllegalArgumentException()
        .isThrownBy(() -> calendar.sessionsEnding(TUESDAY_AFTER_EASTER, 0));
  }

  private List<LocalDate> sessionsIn(int year) {
    return Stream.of(year)
        .flatMap(
            y ->
                calendar
                    .tradingDaysBetween(LocalDate.of(y, 1, 1), LocalDate.of(y, 12, 31))
                    .stream())
        .toList();
  }
}
