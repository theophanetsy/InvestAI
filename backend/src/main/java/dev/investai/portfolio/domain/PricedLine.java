package dev.investai.portfolio.domain;

import org.jspecify.annotations.Nullable;

/**
 * A declared line with its latest close.
 *
 * @param line the declared line
 * @param quote its latest close in euros, {@code null} when none is known ("price unavailable")
 */
public record PricedLine(PortfolioLine line, @Nullable Quote quote) {}
