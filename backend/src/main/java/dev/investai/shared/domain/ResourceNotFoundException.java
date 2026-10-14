package dev.investai.shared.domain;

/** The requested resource does not exist (HTTP 404). Modules distinguish cases by code. */
public final class ResourceNotFoundException extends DomainException {

  private static final long serialVersionUID = 1L;

  /**
   * Creates the exception.
   *
   * @param code stable identifier such as {@code account.not-found}
   * @param message diagnostic message, for logs only
   */
  public ResourceNotFoundException(String code, String message) {
    super(code, message);
  }
}
