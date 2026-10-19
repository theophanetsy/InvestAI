package dev.investai.market.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Computes the closes of one session from a stream of MiFIR trades (ADR-0009): for each tracked
 * instrument, the last trade of the central order book ({@code MmtMarketMechanism = 1}, auctions
 * included) on its listing venue. Trades are fed one at a time, in file order, so that a whole file
 * never sits in memory.
 *
 * <p>Order is the trading time; equal times keep the file order. Not thread-safe: one instance per
 * file.
 */
public final class SessionCloseCalculator {

  /** {@code MmtMarketMechanism} of the central limit order book. */
  public static final int CENTRAL_ORDER_BOOK = 1;

  /** Time zone of Euronext Paris sessions. */
  public static final ZoneId MARKET_ZONE = ZoneId.of("Europe/Paris");

  private final LocalDate session;
  private final Map<Isin, List<Instrument>> trackedByIsin;
  private final Map<Listing, Candidate> lastTrades = new HashMap<>();
  private final Set<Listing> tradedElsewhere = new HashSet<>();

  /**
   * Creates a calculator.
   *
   * @param tracked instruments that need a close
   * @param session the session the file is expected to contain
   */
  public SessionCloseCalculator(Collection<Instrument> tracked, LocalDate session) {
    this.session = session;
    this.trackedByIsin =
        tracked.stream().collect(Collectors.groupingBy(instrument -> instrument.listing().isin()));
  }

  /**
   * Takes one trade into account.
   *
   * @param trade the next trade of the file
   */
  public void accept(MifirTrade trade) {
    List<Instrument> instruments = trackedByIsin.get(trade.isin());
    if (instruments == null || !session.equals(sessionOf(trade))) {
      return;
    }
    for (Instrument instrument : instruments) {
      Listing listing = instrument.listing();
      if (!listing.mic().equals(trade.venue())) {
        tradedElsewhere.add(listing);
      } else if (trade.mechanism() == CENTRAL_ORDER_BOOK) {
        offer(listing, trade);
      }
    }
  }

  /**
   * Returns the session date of a trade, in the market time zone.
   *
   * @param trade a trade
   * @return its session
   */
  public static LocalDate sessionOf(MifirTrade trade) {
    return LocalDate.ofInstant(trade.tradingTime(), MARKET_ZONE);
  }

  private void offer(Listing listing, MifirTrade trade) {
    Candidate current = lastTrades.get(listing);
    if (current == null || trade.tradingTime().isAfter(current.trade().tradingTime())) {
      lastTrades.put(listing, new Candidate(trade, false));
    } else if (trade.tradingTime().equals(current.trade().tradingTime())) {
      boolean differentPrice = trade.price().compareTo(current.trade().price()) != 0;
      lastTrades.put(listing, new Candidate(trade, current.ambiguous() || differentPrice));
    }
  }

  /**
   * Returns the closes once the whole file has been read.
   *
   * @return closes and anomalies to report
   */
  public SessionCloses result() {
    List<DailyClose> closes = new ArrayList<>();
    Set<Listing> ambiguous = new LinkedHashSet<>();
    Set<Listing> elsewhereOnly = new LinkedHashSet<>();
    Set<Listing> notStorable = new LinkedHashSet<>();
    trackedByIsin.values().stream()
        .flatMap(List::stream)
        .forEach(
            instrument -> {
              Listing listing = instrument.listing();
              Candidate last = lastTrades.get(listing);
              if (last == null) {
                if (tradedElsewhere.contains(listing)) {
                  elsewhereOnly.add(listing);
                }
                return;
              }
              BigDecimal price = last.trade().price();
              Prices.exact(price)
                  .ifPresentOrElse(
                      close -> closes.add(closeOf(instrument, last.trade(), close)),
                      () -> notStorable.add(listing));
              if (last.ambiguous()) {
                ambiguous.add(listing);
              }
            });
    return new SessionCloses(closes, ambiguous, elsewhereOnly, notStorable);
  }

  private DailyClose closeOf(Instrument instrument, MifirTrade trade, BigDecimal close) {
    return new DailyClose(instrument.id(), session, close, trade.currency(), PriceSource.EURONEXT);
  }

  /** Last trade seen for a listing, and whether another trade had the same time and price. */
  private record Candidate(MifirTrade trade, boolean ambiguous) {}
}
