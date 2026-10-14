package dev.investai.shared.logging;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class LogSanitizerTest {

  @Test
  void replacesLineBreaksSoThatLogLinesCannotBeForged() {
    assertThat(LogSanitizer.oneLine("id-1\r\nINFO forged line"))
        .isEqualTo("id-1__INFO forged line");
  }

  @Test
  void replacesOtherControlCharactersAndUnicodeLineSeparators() {
    assertThat(LogSanitizer.oneLine("a\u001B[31mb\u0085c\u2028d\u2029e\u000Bf\fg\th"))
        .isEqualTo("a_[31mb_c_d_e_f_g_h");
  }

  @Test
  void keepsSingleLineValuesUnchanged() {
    assertThat(LogSanitizer.oneLine("Account 42 not found")).isEqualTo("Account 42 not found");
  }

  @Test
  void rendersNullExplicitly() {
    assertThat(LogSanitizer.oneLine(null)).isEqualTo("null");
  }
}
