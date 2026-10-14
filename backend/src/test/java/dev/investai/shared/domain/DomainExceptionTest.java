package dev.investai.shared.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class DomainExceptionTest {

  @Test
  void keepsCodeAndMessage() {
    var exception = new ResourceNotFoundException("account.not-found", "Account 42 not found");

    assertThat(exception.code()).isEqualTo("account.not-found");
    assertThat(exception).hasMessage("Account 42 not found");
  }

  @Test
  void acceptsDottedAndDashedLowercaseCodes() {
    var exception = new BusinessRuleException("pea.deposit-cap.exceeded", "Cap exceeded");

    assertThat(exception.code()).isEqualTo("pea.deposit-cap.exceeded");
  }

  @Test
  void rejectsCodesThatAreNotStableIdentifiers() {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new BusinessRuleException("Account Not Found", "message"));
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new ResourceNotFoundException("", "message"));
  }

  @ParameterizedTest
  @ValueSource(strings = {"a.", ".a", "a..b", "a-.b", "a-", "Upper.case", "a b"})
  void rejectsMalformedSeparators(String code) {
    assertThatIllegalArgumentException().isThrownBy(() -> new BusinessRuleException(code, "m"));
  }
}
