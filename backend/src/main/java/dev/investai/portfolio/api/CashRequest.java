package dev.investai.portfolio.api;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/**
 * Cash available on the PEA.
 *
 * @param amount in euros: decimal string, zero or more, at most 2 decimal places
 */
public record CashRequest(
    @NotNull
        @Pattern(regexp = InputFormats.AMOUNT)
        @Schema(example = "350.00", pattern = InputFormats.AMOUNT)
        String amount) {}
