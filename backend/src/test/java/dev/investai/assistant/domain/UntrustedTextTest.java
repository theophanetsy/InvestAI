package dev.investai.assistant.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import org.junit.jupiter.api.Test;

/** Cleaning of third-party and owner text before it reaches the model (spec 3.1, criterion 7). */
class UntrustedTextTest {

  @Test
  void invisibleCharactersAreRemovedAndTheRestKeptIntact() {
    // Zero width space (Cf), right-to-left override (Cf), private use (Co), unassigned (Cn),
    // escape and bell (Cc), a lone surrogate.
    String name = "Air\u200b Liq\u202euide\ue000\u0378\u001b\u0007 \"SA\" {x}\ud800";

    assertThat(UntrustedText.clean(name, 100)).isEqualTo("Air Liquide \"SA\" {x}");
  }

  @Test
  void tabsAndLineBreaksBecomeSpaces() {
    assertThat(UntrustedText.clean("a\tb\nc\r\nd\u2028e\u2029f\u0085g", 100))
        .isEqualTo("a b c  d e f g");
  }

  @Test
  void longTextIsTruncatedWithAnEllipsisWithinTheBound() {
    String cleaned = UntrustedText.clean("x".repeat(300), 100);

    assertThat(cleaned).hasSize(100).endsWith("x…");
    assertThat(UntrustedText.clean("x".repeat(100), 100)).isEqualTo("x".repeat(100));
  }

  @Test
  void boundCountsCodePointsAndNeverSplitsASurrogatePair() {
    String emoji = "😀";

    String cleaned = UntrustedText.clean(emoji.repeat(150), 100);

    assertThat(cleaned.codePointCount(0, cleaned.length())).isEqualTo(100);
    assertThat(cleaned).isEqualTo(emoji.repeat(99) + "…");
  }

  @Test
  void theBoundIsCheckedAfterCleaning() {
    // 100 visible characters plus invisible ones: nothing to truncate.
    assertThat(UntrustedText.clean("y".repeat(100) + "\u200b".repeat(50), 100))
        .isEqualTo("y".repeat(100));
  }

  @Test
  void boundMustLeaveRoomForTheEllipsis() {
    assertThatIllegalArgumentException().isThrownBy(() -> UntrustedText.clean("abc", 1));
  }
}
