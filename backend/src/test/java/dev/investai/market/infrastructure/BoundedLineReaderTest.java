package dev.investai.market.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.StringReader;
import org.junit.jupiter.api.Test;

class BoundedLineReaderTest {

  @Test
  void readsLinesWhateverTheirTerminator() throws IOException {
    var reader = new BoundedLineReader(new StringReader("a\r\nbb\n\nccc"), 10);

    assertThat(reader.readLine()).isEqualTo("a");
    assertThat(reader.readLine()).isEqualTo("bb");
    assertThat(reader.readLine()).isEmpty();
    assertThat(reader.readLine()).isEqualTo("ccc");
    assertThat(reader.readLine()).isNull();
  }

  @Test
  void emptyInputHasNoLine() throws IOException {
    assertThat(new BoundedLineReader(new StringReader(""), 10).readLine()).isNull();
  }

  @Test
  void lineLongerThanTheLimitFailsBeforeFillingTheMemory() {
    var reader = new BoundedLineReader(new StringReader("x".repeat(100_000)), 1_000);

    assertThatThrownBy(reader::readLine)
        .isInstanceOf(BoundedLineReader.LineTooLongException.class)
        .hasMessage("line longer than 1000 characters");
  }

  @Test
  void lineOfExactlyTheLimitIsAccepted() throws IOException {
    assertThat(new BoundedLineReader(new StringReader("12345\n"), 5).readLine()).isEqualTo("12345");
  }
}
