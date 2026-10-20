package dev.investai.portfolio.domain;

import java.util.List;
import java.util.Optional;

/** Port: the declared PEA, its lines and its cash. */
public interface PortfolioRepository {

  /**
   * Lists the declared lines.
   *
   * @return the lines, in no particular order
   */
  List<PortfolioLine> findLines();

  /**
   * Finds a line.
   *
   * @param id its identifier
   * @return the line, if known
   */
  Optional<PortfolioLine> findLine(PortfolioLineId id);

  /**
   * Adds a line, unless its instrument already has one (checked atomically).
   *
   * @param line the new line
   * @return {@code false} when the instrument already has a line
   */
  boolean insert(PortfolioLine line);

  /**
   * Replaces the declared values of a line.
   *
   * @param line the line, with its identifier
   * @return {@code false} when the line is unknown
   */
  boolean update(PortfolioLine line);

  /**
   * Removes a line.
   *
   * @param id its identifier
   * @return {@code false} when the line is unknown
   */
  boolean delete(PortfolioLineId id);

  /**
   * Returns the declared cash.
   *
   * @return the cash, empty until the owner declares it
   */
  Optional<Cash> findCash();

  /**
   * Creates or replaces the cash (idempotent).
   *
   * @param cash the cash
   */
  void saveCash(Cash cash);
}
