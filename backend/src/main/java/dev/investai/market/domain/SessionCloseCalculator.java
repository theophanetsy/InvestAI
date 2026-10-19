package dev.investai.market.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;

/**
 * Computes the closes of one session from a stream of MiFIR trades (ADR-0009): for each instrument
 * of the reference data, the last trade of the central order book ({@code MmtMarketMechanism = 1},
 * auctions included) on its listing venue, with the volume and the number of those trades. Trades
 * are fed one at a time, in file order, so that a whole file never sits in memory; only one state
 * per listing is kept.
 *
 * <p>Order is the trading time; equal times keep the file order. Not thread-safe: one instance per
 * file.
 */
public final class SessionCloseCalculator {

  /** {@code MmtMarketMechanism} of the central limit order book. */
  public static final int CENTRAL_ORDER_BOOK = 1;

  /**
   * Distinct unknown ISINs remembered: bounds the memory taken by a forged file (millions of short
   * lines with distinct ISINs); beyond, only the trades are counted.
   */
  public static final int MAX_UNKNOWN_ISINS = 10_000;

  private final LocalDate session;
  private final Map<Isin, List<Instrument>> instrumentsByIsin;
  private final Map<Listing, ListingActivity> activities = new HashMap<>();
  private final Set<Listing> tradedElsewhere = new HashSet<>();
  private final Set<Listing> otherCurrency = new HashSet<>();
  private final Set<Isin> unknownIsins = new HashSet<>();
  private final int maxUnknownIsins;
  private long unknownTrades;

  /**
   * Creates a calculator.
   *
   * @param instruments the reference data: every instrument that may get a close
   * @param session the session the file is expected to contain
   */
  public SessionCloseCalculator(Collection<Instrument> instruments, LocalDate session) {
    this(instruments, session, MAX_UNKNOWN_ISINS);
  }

  /**
   * Creates a calculator with a given bound on the distinct unknown ISINs remembered.
   *
   * @param instruments the reference data: every instrument that may get a close
   * @param session the session the file is expected to contain
   * @param maxUnknownIsins distinct unknown ISINs remembered at most
   */
  public SessionCloseCalculator(
      Collection<Instrument> instruments, LocalDate session, int maxUnknownIsins) {
    this.session = session;
    this.maxUnknownIsins = maxUnknownIsins;
    this.instrumentsByIsin =
        instruments.stream()
            .collect(Collectors.groupingBy(instrument -> instrument.listing().isin()));
  }

  /**
   * Takes one trade into account.
   *
   * @param trade the next trade of the file
   */
  public void accept(MifirTrade trade) {
    if (!session.equals(sessionOf(trade))) {
      return;
    }
    List<Instrument> instruments = instrumentsByIsin.get(trade.isin());
    if (instruments == null) {
      if (unknownIsins.size() < maxUnknownIsins) {
        unknownIsins.add(trade.isin());
      }
      unknownTrades++;
      return;
    }
    for (Instrument instrument : instruments) {
      Listing listing = instrument.listing();
      if (!listing.mic().equals(trade.venue())) {
        tradedElsewhere.add(listing);
      } else if (trade.mechanism() == CENTRAL_ORDER_BOOK) {
        offerInInstrumentCurrency(instrument, trade);
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
    return LocalDate.ofInstant(trade.tradingTime(), EuronextCalendar.ZONE);
  }

  private void offerInInstrumentCurrency(Instrument instrument, MifirTrade trade) {
    if (!trade.currency().equals(instrument.currency())) {
      otherCurrency.add(instrument.listing());
      return;
    }
    ListingActivity activity = activities.get(instrument.listing());
    if (activity == null) {
      activities.put(instrument.listing(), new ListingActivity(trade));
    } else {
      activity.add(trade);
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
    instrumentsByIsin.values().stream()
        .flatMap(List::stream)
        .forEach(
            instrument -> {
              Listing listing = instrument.listing();
              ListingActivity activity = activities.get(listing);
              if (activity == null) {
                if (tradedElsewhere.contains(listing)) {
                  elsewhereOnly.add(listing);
                }
                return;
              }
              Prices.exact(activity.last.price())
                  .ifPresentOrElse(
                      close -> closes.add(closeOf(instrument, close, activity)),
                      () -> notStorable.add(listing));
              if (activity.ambiguous) {
                ambiguous.add(listing);
              }
            });
    return new SessionCloses(
        closes,
        ambiguous,
        elsewhereOnly,
        notStorable,
        otherCurrency,
        unknownIsins.size(),
        unknownTrades);
  }

  private DailyClose closeOf(Instrument instrument, BigDecimal close, ListingActivity activity) {
    return new DailyClose(
        instrument.id(),
        session,
        close,
        instrument.currency(),
        PriceSource.EURONEXT,
        activity.volume(),
        activity.trades);
  }

  /**
   * Trades of a listing eligible for its close: the last one (and whether another trade had the
   * same time with a different price), their number and their total quantity.
   */
  private static final class ListingActivity {
    private MifirTrade last;
    private boolean ambiguous;
    private int trades;
    private long volume;
    private boolean volumeKnown = true;

    ListingActivity(MifirTrade first) {
      last = first;
      count(first);
    }

    void add(MifirTrade trade) {
      count(trade);
      if (trade.tradingTime().isAfter(last.tradingTime())) {
        last = trade;
        ambiguous = false;
      } else if (trade.tradingTime().equals(last.tradingTime())) {
        ambiguous = ambiguous || trade.price().compareTo(last.price()) != 0;
        last = trade;
      }
    }

    private void count(MifirTrade trade) {
      trades++;
      if (!volumeKnown) {
        return;
      }
      Optional<Long> units = Volumes.units(trade.quantity());
      if (units.isEmpty()) {
        volumeKnown = false;
        return;
      }
      try {
        volume = Math.addExact(volume, units.get());
      } catch (ArithmeticException ex) {
        // Beyond a bigint: unknown rather than wrong.
        volumeKnown = false;
      }
    }

    @Nullable Long volume() {
      return volumeKnown ? volume : null;
    }
  }
}
