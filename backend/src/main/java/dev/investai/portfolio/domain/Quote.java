package dev.investai.portfolio.domain;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Latest known close of a held instrument, in euros.
 *
 * @param close the raw close of the market module
 * @param date the session of the close
 */
public record Quote(BigDecimal close, LocalDate date) {}
