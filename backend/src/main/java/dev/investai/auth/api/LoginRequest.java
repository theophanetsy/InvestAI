package dev.investai.auth.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Login form.
 *
 * @param username the login
 * @param password the clear password, only kept for the duration of the request
 */
public record LoginRequest(
    @NotBlank @Size(max = 100) String username, @NotBlank @Size(max = 200) String password) {

  @Override
  public String toString() {
    return "LoginRequest[<redacted>]";
  }
}
