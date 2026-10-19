package dev.investai.market.application;

import dev.investai.market.domain.MarketDataUnavailableException;
import dev.investai.market.domain.MifirTrade;
import dev.investai.market.domain.SessionCloseCalculator;
import dev.investai.market.domain.TradeFileSegment;
import dev.investai.market.domain.TradeFileSource;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/** Euronext files held in memory, by segment. */
final class FakeTradeFiles implements TradeFileSource {
  private final Map<TradeFileSegment, List<MifirTrade>> files =
      new EnumMap<>(TradeFileSegment.class);
  private final List<TradeFileSegment> downloads = new ArrayList<>();
  private boolean failing;

  void publish(TradeFileSegment segment, MifirTrade... trades) {
    files.put(segment, List.of(trades));
  }

  void failing(boolean value) {
    failing = value;
  }

  List<TradeFileSegment> downloads() {
    return downloads;
  }

  @Override
  public Summary read(TradeFileSegment segment, Consumer<MifirTrade> consumer) {
    downloads.add(segment);
    if (failing) {
      throw new MarketDataUnavailableException("Euronext " + segment + " failed: HTTP 503");
    }
    List<MifirTrade> trades = files.getOrDefault(segment, List.of());
    LocalDate session = null;
    for (MifirTrade trade : trades) {
      consumer.accept(trade);
      LocalDate day = SessionCloseCalculator.sessionOf(trade);
      session = session == null || day.isAfter(session) ? day : session;
    }
    return new Summary(session, trades.size(), 0);
  }
}
