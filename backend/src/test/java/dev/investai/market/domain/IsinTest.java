package dev.investai.market.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class IsinTest {

  /** Panel of ADR-0009 plus a non-European ISIN. */
  @ParameterizedTest
  @ValueSource(
      strings = {
        "FR0000120073",
        "FR0000120271",
        "FR0000033888",
        "NL0010273215",
        "LU1681043599",
        "IE0002XZSHO1",
        "FR0007052782",
        "US0378331005"
      })
  void acceptsRealIsins(String value) {
    assertThat(Isin.isValid(value)).isTrue();
    assertThat(new Isin(value).value()).isEqualTo(value);
    assertThat(new Isin(value)).hasToString(value);
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(
      strings = {
        "FR0000120074", // wrong check digit
        "IE0002XZSHO2", // wrong check digit, letters in the body
        "fr0000120073", // lower case
        "FR000012007", // too short
        "FR00001200733", // too long
        "F10000120073", // country code with a digit
        "FR000012007A", // check character is not a digit
        " FR0000120073"
      })
  void rejectsMalformedIsins(String value) {
    assertThat(Isin.isValid(value)).isFalse();
  }

  @ParameterizedTest
  @ValueSource(strings = {"FR0000120074", "XX"})
  void constructorRejectsInvalidValueWithoutEchoingIt(String value) {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new Isin(value))
        .withMessage("Invalid ISIN");
  }
}
