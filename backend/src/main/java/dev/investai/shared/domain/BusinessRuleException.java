package dev.investai.shared.domain;

/** The request is well-formed but violates a business rule (HTTP 422). Cases differ by code. */
public final class BusinessRuleException extends DomainException {

  private static final long serialVersionUID = 1L;

  /**
   * Creates the exception.
   *
   * @param code stable identifier such as {@code pea.deposit-cap.exceeded}
   * @param message diagnostic message, for logs only
   */
  public BusinessRuleException(String code, String message) {
    super(code, message);
  }
}
