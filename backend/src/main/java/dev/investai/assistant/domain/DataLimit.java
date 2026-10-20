package dev.investai.assistant.domain;

/**
 * A limit of the data, recalled in every tool output that carries closes or indicators (spec 3.1,
 * criterion 6): the model reads it at each call, even when the client does not pass the
 * instructions of the server on. Constant French text of the application, sent as is.
 */
public enum DataLimit {
  /** Closes are not adjusted for splits nor bonus shares. */
  RAW_CLOSES(
      "Clôtures brutes, non ajustées des divisions ni des attributions gratuites : l'année d'une"
          + " telle opération, la performance et la pire baisse de la valeur sont faussées par une"
          + " chute artificielle du cours."),
  /** Performances exclude dividends. */
  EXCLUDES_DIVIDENDS(
      "Performances hors dividendes : une valeur ou un ETF qui distribue paraît moins performant"
          + " qu'un ETF qui capitalise."),
  /** An outlier quote or a split can top a sort by performance. */
  OUTLIER_QUOTES(
      "Une cotation aberrante ou une division peut placer une valeur en tête d'un tri par"
          + " performance : vérifier son historique avant de la retenir."),
  /** Instruments that are not tracked have a short history, with gaps. */
  SHORT_HISTORY(
      "Une valeur non suivie n'a que les séances collectées depuis la mise en service, avec des"
          + " trous possibles ; sous 80 % des séances, l'indicateur vaut INSUFFICIENT_HISTORY."),
  /** Volatility of illiquid instruments is underestimated; liquidity needs three months. */
  LOW_LIQUIDITY(
      "La volatilité d'une valeur peu échangée est sous-estimée ; la liquidité (volumes Euronext"
          + " sur 3 mois) reste indisponible tant que trois mois de volumes ne sont pas collectés."),
  /** PEA eligibility is an indication. */
  PEA_ELIGIBILITY_INDICATIVE(
      "Éligibilité PEA indicative : règle sur le pays de l'ISIN pour les actions, inconnue par"
          + " défaut pour les ETF ; à vérifier chez le courtier avant l'ordre."),
  /** The latest close is not the execution price; brokerage fees are not counted. */
  CLOSE_IS_NOT_ORDER_PRICE(
      "La dernière clôture n'est pas le prix d'exécution : le cours aura bougé au moment de"
          + " l'ordre, et les frais de courtage ne sont pas comptés.");

  private final String text;

  DataLimit(String text) {
    this.text = text;
  }

  /**
   * Returns the French text recalled to the model.
   *
   * @return the constant text
   */
  public String text() {
    return text;
  }
}
