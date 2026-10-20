package dev.investai.market.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Raw closes of one instrument (or values of a portfolio) on consecutive sessions of the Euronext
 * calendar, with the sessions that have none. Never interpolated (spec 2.4, criterion 1): a window
 * is usable when at least 80 % of its sessions have a close.
 */
public final class ClosingPrices {

  private static final int COVERAGE_NUMERATOR = 4;
  private static final int COVERAGE_DENOMINATOR = 5;

  private final List<LocalDate> sessions;
  private final Set<LocalDate> sessionSet;
  private final Map<LocalDate, BigDecimal> values;
  private final Map<LocalDate, BigDecimal> tradedAmounts;

  private ClosingPrices(
      List<LocalDate> sessions,
      Map<LocalDate, BigDecimal> values,
      Map<LocalDate, BigDecimal> tradedAmounts) {
    this.sessions = List.copyOf(sessions);
    this.sessionSet = Set.copyOf(sessions);
    this.values = Map.copyOf(values);
    this.tradedAmounts = Map.copyOf(tradedAmounts);
  }

  /**
   * Places stored closes on sessions; closes of other dates are ignored. The traded amount of a
   * session (close × volume) is only kept for Euronext closes with a known volume: EODHD volumes
   * have another definition (spec 2.2).
   *
   * @param sessions the sessions, in chronological order
   * @param closes closes of one instrument
   * @return the series
   */
  public static ClosingPrices of(List<LocalDate> sessions, Collection<DailyClose> closes) {
    Set<LocalDate> expected = Set.copyOf(sessions);
    Map<LocalDate, BigDecimal> values = new HashMap<>();
    Map<LocalDate, BigDecimal> amounts = new HashMap<>();
    for (DailyClose close : closes) {
      LocalDate session = close.tradingDate();
      if (expected.contains(session)) {
        values.put(session, close.close());
        Long volume = close.volume();
        if (volume != null && close.source() == PriceSource.EURONEXT) {
          amounts.put(session, close.close().multiply(BigDecimal.valueOf(volume)));
        }
      }
    }
    return new ClosingPrices(sessions, values, amounts);
  }

  /**
   * A series of computed values (e.g. a portfolio value), without traded amounts.
   *
   * @param sessions the sessions, in chronological order
   * @param values positive values by session; values of other dates are ignored
   * @return the series
   */
  public static ClosingPrices ofValues(
      List<LocalDate> sessions, Map<LocalDate, BigDecimal> values) {
    Set<LocalDate> expected = Set.copyOf(sessions);
    Map<LocalDate, BigDecimal> kept = new HashMap<>(values);
    kept.keySet().retainAll(expected);
    return new ClosingPrices(sessions, kept, Map.of());
  }

  /**
   * Tells whether enough sessions are known: at least 80 % (rounded up).
   *
   * @param known sessions with a value
   * @param expected sessions of the window
   * @return {@code true} when usable
   */
  public static boolean covers(int known, int expected) {
    return known * COVERAGE_DENOMINATOR >= expected * COVERAGE_NUMERATOR;
  }

  /**
   * The last sessions of the series.
   *
   * @param count number of sessions, at most the size of the series
   * @return the shorter series
   */
  public ClosingPrices lastSessions(int count) {
    if (count > sessions.size()) {
      throw new IllegalArgumentException("Window longer than the series");
    }
    return new ClosingPrices(
        sessions.subList(sessions.size() - count, sessions.size()), values, tradedAmounts);
  }

  /**
   * The sessions of the series.
   *
   * @return the sessions, in chronological order
   */
  public List<LocalDate> sessions() {
    return sessions;
  }

  /**
   * Number of sessions with a value.
   *
   * @return the known sessions
   */
  public int knownCount() {
    return (int) sessions.stream().filter(values::containsKey).count();
  }

  /**
   * Tells whether at least 80 % of the sessions have a value.
   *
   * @return {@code true} when the series is usable
   */
  public boolean sufficient() {
    return covers(knownCount(), sessions.size());
  }

  /**
   * The value of a session.
   *
   * @param session the session
   * @return the value, empty when unknown or outside the series
   */
  public Optional<BigDecimal> on(LocalDate session) {
    return sessionSet.contains(session)
        ? Optional.ofNullable(values.get(session))
        : Optional.empty();
  }

  /**
   * The known values, in chronological order.
   *
   * @return the values, without the missing sessions
   */
  public List<BigDecimal> knownValues() {
    return sessions.stream().filter(values::containsKey).map(values::get).toList();
  }

  /**
   * The traded amount (Euronext close × volume) of a session.
   *
   * @param session the session
   * @return the amount, empty when unknown or outside the series
   */
  public Optional<BigDecimal> tradedAmountOn(LocalDate session) {
    return sessionSet.contains(session)
        ? Optional.ofNullable(tradedAmounts.get(session))
        : Optional.empty();
  }

  /**
   * Returns between consecutive sessions that both have a value.
   *
   * @return one return per session after the first, {@code NaN} when either value is missing
   */
  public DailyReturns dailyReturns() {
    double[] returns = new double[Math.max(0, sessions.size() - 1)];
    for (int i = 1; i < sessions.size(); i++) {
      BigDecimal previous = values.get(sessions.get(i - 1));
      BigDecimal current = values.get(sessions.get(i));
      returns[i - 1] =
          previous == null || current == null
              ? Double.NaN
              : current.doubleValue() / previous.doubleValue() - 1.0;
    }
    return new DailyReturns(returns);
  }
}
