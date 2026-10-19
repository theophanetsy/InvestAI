package dev.investai.market.domain;

import java.time.LocalDate;
import java.time.Period;
import org.jspecify.annotations.Nullable;

/**
 * Period of a price history request.
 *
 * @param from first session, inclusive
 * @param to last session, inclusive
 */
public record PriceRange(LocalDate from, LocalDate to) {

  /** Period returned when the client does not give a start. */
  public static final Period DEFAULT_SPAN = Period.ofYears(1);

  /** Longest period a client may request. */
  public static final Period MAX_SPAN = Period.ofYears(5);

  /**
   * Applies the defaults (one year up to today) and checks the bounds.
   *
   * @param from requested start, or {@code null}
   * @param to requested end, or {@code null} for today
   * @param today current date
   * @return the period
   * @throws InvalidPriceRangeException when {@code from} is after {@code to} or the period is
   *     longer than {@link #MAX_SPAN}
   */
  public static PriceRange resolve(
      @Nullable LocalDate from, @Nullable LocalDate to, LocalDate today) {
    LocalDate end = to == null ? today : to;
    LocalDate start = from == null ? end.minus(DEFAULT_SPAN) : from;
    if (start.isAfter(end)) {
      throw new InvalidPriceRangeException("from", InvalidPriceRangeException.NOT_AFTER_TO);
    }
    if (start.isBefore(end.minus(MAX_SPAN))) {
      throw new InvalidPriceRangeException("from", InvalidPriceRangeException.MAX_FIVE_YEARS);
    }
    return new PriceRange(start, end);
  }
}
