package dev.investai.portfolio.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * What the owner wants (spec 2.3, criterion 1): one profile, single user. The objectives are free
 * text typed by the owner: untrusted data, stored as is, never instructions for the agent.
 *
 * @param amountToInvest single sum to invest at once, in euros, positive, at most 2 decimal places
 * @param horizonYears investment horizon, {@value #MIN_HORIZON_YEARS} to {@value
 *     #MAX_HORIZON_YEARS} years
 * @param riskProfile risk profile chosen by the owner
 * @param exclusions sectors or instruments to avoid, at most {@value #MAX_EXCLUSIONS}; trimmed,
 *     duplicates ignoring case dropped
 * @param objectives free text, at most {@value #MAX_OBJECTIVES_LENGTH} characters, {@code null}
 *     when not given or blank
 * @param peaOpenedOn opening date of the PEA (its age drives the taxation), {@code null} when not
 *     given
 * @param updatedAt last change
 */
public record InvestorProfile(
    BigDecimal amountToInvest,
    int horizonYears,
    RiskProfile riskProfile,
    List<String> exclusions,
    @Nullable String objectives,
    @Nullable LocalDate peaOpenedOn,
    Instant updatedAt) {

  /** Shortest horizon, in years. */
  public static final int MIN_HORIZON_YEARS = 1;

  /** Longest horizon, in years. */
  public static final int MAX_HORIZON_YEARS = 40;

  /** Maximum number of exclusions. */
  public static final int MAX_EXCLUSIONS = 20;

  /** Maximum length of one exclusion. */
  public static final int MAX_EXCLUSION_LENGTH = 100;

  /** Maximum length of the objectives. */
  public static final int MAX_OBJECTIVES_LENGTH = 500;

  /** Checks the invariants; the messages never echo the values. */
  public InvestorProfile {
    amountToInvest = positiveCents(amountToInvest);
    if (horizonYears < MIN_HORIZON_YEARS || horizonYears > MAX_HORIZON_YEARS) {
      throw new IllegalArgumentException("Horizon out of range");
    }
    exclusions = List.copyOf(normalized(exclusions));
    checkExclusions(exclusions);
    objectives = validObjectives(objectives);
  }

  private static BigDecimal positiveCents(BigDecimal amount) {
    if (amount.signum() <= 0) {
      throw new IllegalArgumentException("Amount to invest not positive");
    }
    return Amounts.exact(amount, Amounts.CENTS);
  }

  private static void checkExclusions(List<String> exclusions) {
    if (exclusions.size() > MAX_EXCLUSIONS
        || exclusions.stream().anyMatch(InvestorProfile::invalidExclusion)) {
      throw new IllegalArgumentException("Invalid exclusions");
    }
  }

  /** Blank means not given. */
  private static @Nullable String validObjectives(@Nullable String objectives) {
    if (objectives == null || objectives.isBlank()) {
      return null;
    }
    if (objectives.length() > MAX_OBJECTIVES_LENGTH) {
      throw new IllegalArgumentException("Objectives too long");
    }
    return objectives;
  }

  /** Trimmed, duplicates (ignoring case) dropped, first spelling kept, order kept. */
  private static List<String> normalized(List<String> exclusions) {
    Map<String, String> byKey = new LinkedHashMap<>();
    for (String exclusion : exclusions) {
      String trimmed = exclusion.strip();
      byKey.putIfAbsent(trimmed.toLowerCase(Locale.ROOT), trimmed);
    }
    return new ArrayList<>(byKey.values());
  }

  private static boolean invalidExclusion(String exclusion) {
    return exclusion.isBlank() || exclusion.length() > MAX_EXCLUSION_LENGTH;
  }
}
