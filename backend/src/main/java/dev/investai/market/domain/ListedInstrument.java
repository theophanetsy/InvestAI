package dev.investai.market.domain;

import java.util.Currency;

/**
 * One entry of the reference data downloaded from a provider.
 *
 * @param listing ISIN and venue
 * @param ticker exchange ticker
 * @param name instrument name
 * @param type kind of instrument
 * @param currency trading currency
 */
public record ListedInstrument(
    Listing listing, String ticker, String name, InstrumentType type, Currency currency) {}
