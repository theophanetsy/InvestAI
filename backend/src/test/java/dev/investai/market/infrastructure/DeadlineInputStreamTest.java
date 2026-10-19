package dev.investai.market.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class DeadlineInputStreamTest {

  private static final byte[] DATA = {1, 2, 3};

  @Test
  void readsWhileTheDeadlineIsNotReached() throws IOException {
    try (var in = new DeadlineInputStream(new ByteArrayInputStream(DATA), Duration.ofMinutes(1))) {
      assertThat(in.read()).isEqualTo(1);
      assertThat(in.readAllBytes()).containsExactly(2, 3);
    }
  }

  @Test
  void failsOnceTheDeadlineIsReached() throws IOException {
    try (var in = new DeadlineInputStream(new ByteArrayInputStream(DATA), Duration.ZERO)) {
      assertThatThrownBy(in::read)
          .isInstanceOf(DeadlineInputStream.DeadlineExceededException.class)
          .hasMessage("download longer than PT0S");
      assertThatThrownBy(() -> in.read(new byte[2], 0, 2))
          .isInstanceOf(DeadlineInputStream.DeadlineExceededException.class);
    }
  }
}
