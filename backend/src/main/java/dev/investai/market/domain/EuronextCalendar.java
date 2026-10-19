package dev.investai.market.domain;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.MonthDay;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Trading days of Euronext cash markets: weekdays except 1 January, Good Friday, Easter Monday, 1
 * May, 25 and 26 December. Easter is computed (anonymous Gregorian algorithm), never hard-coded.
 */
public final class EuronextCalendar {

  /** Time zone of Euronext Paris: sessions are dates in this zone. */
  public static final ZoneId ZONE = ZoneId.of("Europe/Paris");

  private static final int MIN_YEAR = 1900;
  private static final int MAX_YEAR = 2199;
  private static final Set<MonthDay> FIXED_HOLIDAYS =
      Set.of(MonthDay.of(1, 1), MonthDay.of(5, 1), MonthDay.of(12, 25), MonthDay.of(12, 26));
  private static final int METONIC_CYCLE = 19;
  private static final int CENTURY = 100;
  private static final int LEAP_CYCLE = 4;
  private static final int LUNAR_SHIFT = 8;
  private static final int LUNAR_PERIOD = 25;
  private static final int LUNAR_DIVISOR = 3;
  private static final int EPACT_SHIFT = 15;
  private static final int LUNAR_MONTH = 30;
  private static final int WEEKDAY_SHIFT = 32;
  private static final int DAYS_PER_WEEK = 7;
  private static final int EXCEPTION_EPACT_FACTOR = 11;
  private static final int EXCEPTION_WEEKDAY_FACTOR = 22;
  private static final int EXCEPTION_DIVISOR = 451;
  private static final int MARCH_OFFSET = 114;
  private static final int DAYS_PER_MONTH = 31;

  /**
   * Tells whether the market is open on a date.
   *
   * @param date the date, in the market time zone
   * @return {@code true} on a trading day
   */
  public boolean isTradingDay(LocalDate date) {
    if (date.getYear() < MIN_YEAR || date.getYear() > MAX_YEAR) {
      throw new IllegalArgumentException("Unsupported year: " + date.getYear());
    }
    DayOfWeek day = date.getDayOfWeek();
    return day != DayOfWeek.SATURDAY && day != DayOfWeek.SUNDAY && !isHoliday(date);
  }

  /**
   * Returns the last trading day strictly before a date.
   *
   * @param date the reference date
   * @return the previous session
   */
  public LocalDate previousTradingDay(LocalDate date) {
    LocalDate candidate = date.minusDays(1);
    while (!isTradingDay(candidate)) {
      candidate = candidate.minusDays(1);
    }
    return candidate;
  }

  /**
   * Lists the trading days of a period.
   *
   * @param from first date, inclusive
   * @param to last date, inclusive
   * @return the sessions in chronological order, empty when {@code from} is after {@code to}
   */
  public List<LocalDate> tradingDaysBetween(LocalDate from, LocalDate to) {
    List<LocalDate> sessions = new ArrayList<>();
    for (LocalDate day = from; !day.isAfter(to); day = day.plusDays(1)) {
      if (isTradingDay(day)) {
        sessions.add(day);
      }
    }
    return sessions;
  }

  private static boolean isHoliday(LocalDate date) {
    LocalDate easter = easterSunday(date.getYear());
    return FIXED_HOLIDAYS.contains(MonthDay.from(date))
        || date.equals(easter.minusDays(2))
        || date.equals(easter.plusDays(1));
  }

  /** Anonymous Gregorian algorithm (Meeus/Jones/Butcher); the constants are its coefficients. */
  private static LocalDate easterSunday(int year) {
    int golden = year % METONIC_CYCLE;
    int century = year / CENTURY;
    int yearOfCentury = year % CENTURY;
    int skippedLeapYears = century / LEAP_CYCLE;
    int centuryRemainder = century % LEAP_CYCLE;
    int lunarCorrection = (century - (century + LUNAR_SHIFT) / LUNAR_PERIOD + 1) / LUNAR_DIVISOR;
    int epact =
        (METONIC_CYCLE * golden + century - skippedLeapYears - lunarCorrection + EPACT_SHIFT)
            % LUNAR_MONTH;
    int weekdayCorrection =
        (WEEKDAY_SHIFT
                + 2 * centuryRemainder
                + 2 * (yearOfCentury / LEAP_CYCLE)
                - epact
                - yearOfCentury % LEAP_CYCLE)
            % DAYS_PER_WEEK;
    int exception =
        (golden + EXCEPTION_EPACT_FACTOR * epact + EXCEPTION_WEEKDAY_FACTOR * weekdayCorrection)
            / EXCEPTION_DIVISOR;
    int monthAndDay = epact + weekdayCorrection - DAYS_PER_WEEK * exception + MARCH_OFFSET;
    return LocalDate.of(year, monthAndDay / DAYS_PER_MONTH, monthAndDay % DAYS_PER_MONTH + 1);
  }
}
