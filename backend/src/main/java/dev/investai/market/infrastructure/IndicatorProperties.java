package dev.investai.market.infrastructure;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Benchmarks of the indicators ({@code investai.market.indicators.*}, spec 2.4, criterion 4), as
 * {@code ISIN:MIC}; both must be in {@code investai.market.benchmarks} (tracked: one year of
 * history).
 *
 * @param msciWorld the MSCI World ETF
 * @param cac40 the CAC 40 ETF
 */
@Validated
@ConfigurationProperties("investai.market.indicators")
public record IndicatorProperties(@NotBlank String msciWorld, @NotBlank String cac40) {}
