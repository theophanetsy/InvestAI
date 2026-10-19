package dev.investai.market.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class CsvLineTest {

  @Test
  void splitsUnquotedFields() {
    assertThat(CsvLine.parse("a,b,,c")).hasValue(new String[] {"a", "b", "", "c"});
  }

  @Test
  void unquotesFieldsAndKeepsEscapedQuotes() {
    assertThat(CsvLine.parse("\"709.5500000\",\"\",\"say \"\"hi\"\"\",x"))
        .hasValue(new String[] {"709.5500000", "", "say \"hi\"", "x"});
  }

  @Test
  void trailingSeparatorMeansAnEmptyLastField() {
    assertThat(CsvLine.parse("\"a\",")).hasValue(new String[] {"a", ""});
  }

  @Test
  void rejectsUnterminatedOrMisplacedQuotes() {
    assertThat(CsvLine.parse("\"abc")).isEmpty();
    assertThat(CsvLine.parse("\"abc\"x,y")).isEmpty();
  }
}
