package dev.investai.portfolio.domain;

/**
 * Risk profile chosen directly by the owner among four (spec 2.3, criterion 1). The label and the
 * description are versioned French text: shown as is in the profile page, and given as is to the
 * advice agent (phase 3). Changing them changes what the agent is told: review the evals then.
 */
public enum RiskProfile {
  /** About 10 % yearly loss bearable, about 25 % shares. */
  PRUDENT(
      "Prudent",
      "Vous privilégiez la stabilité du capital. Baisse annuelle supportable indicative : environ"
          + " 10 %. Part indicative d'actions : environ 25 %, le reste en espèces ou en supports"
          + " peu risqués."),
  /** About 20 % yearly loss bearable, about 50 % shares. */
  BALANCED(
      "Équilibré",
      "Vous cherchez un équilibre entre croissance et stabilité. Baisse annuelle supportable"
          + " indicative : environ 20 %. Part indicative d'actions : environ 50 %."),
  /** About 30 % yearly loss bearable, about 75 % shares. */
  DYNAMIC(
      "Dynamique",
      "Vous visez la croissance et acceptez de fortes variations. Baisse annuelle supportable"
          + " indicative : environ 30 %. Part indicative d'actions : environ 75 %."),
  /** 40 % yearly loss or more bearable, up to 100 % shares. */
  AGGRESSIVE(
      "Offensif",
      "Vous recherchez la performance maximale sur le long terme. Baisse annuelle supportable"
          + " indicative : 40 % et plus. Part indicative d'actions : jusqu'à 100 %.");

  private final String label;
  private final String description;

  RiskProfile(String label, String description) {
    this.label = label;
    this.description = description;
  }

  /**
   * Returns the French name of the profile.
   *
   * @return e.g. {@code Équilibré}
   */
  public String label() {
    return label;
  }

  /**
   * Returns the French description: indicative bearable yearly loss and share of equities.
   *
   * @return the description
   */
  public String description() {
    return description;
  }
}
