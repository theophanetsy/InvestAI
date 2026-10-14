package dev.investai.auth.api;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class LoginRequestTest {

  @Test
  void neverPrintsThePassword() {
    var request = new LoginRequest("someone", "visible-if-leaked-1234");

    assertThat(request.toString()).doesNotContain("visible-if-leaked-1234");
  }
}
