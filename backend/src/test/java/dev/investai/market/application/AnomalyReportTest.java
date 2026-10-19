package dev.investai.market.application;

import static dev.investai.market.MarketTestData.isin;
import static org.assertj.core.api.Assertions.assertThat;

import dev.investai.market.domain.Listing;
import dev.investai.market.domain.Mic;
import dev.investai.market.domain.SessionCloses;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

@ExtendWith(OutputCaptureExtension.class)
class AnomalyReportTest {

  private static final LocalDate SESSION = LocalDate.parse("2026-10-16");
  private static final String AMBIGUOUS = "Last trades share a timestamp with different prices";

  @Test
  void eachKindOfAnomalyIsOneLineWithItsCountAndTheFirstTenListings(CapturedOutput output) {
    List<Listing> listings =
        IntStream.range(10, 22)
            .mapToObj(n -> new Listing(isin("FR0000000" + n), Mic.XPAR))
            .toList();
    Listing growth = new Listing(isin("FR000003388"), Mic.ALXP);

    AnomalyReport.log(
        SESSION,
        new SessionCloses(
            List.of(), Set.copyOf(listings), Set.of(growth), Set.of(), Set.of(), 0, 0));

    List<String> lines = output.getOut().lines().filter(l -> l.contains(AMBIGUOUS)).toList();
    assertThat(lines)
        .singleElement()
        .satisfies(
            line -> {
              assertThat(line).contains("count=12", "session=2026-10-16");
              listings.subList(0, 10).forEach(l -> assertThat(line).contains(l.toString()));
              listings.subList(10, 12).forEach(l -> assertThat(line).doesNotContain(l.toString()));
            });
    assertThat(output.getOut())
        .contains("traded on another venue only", "count=1", growth.toString())
        .doesNotContain("more than 4 decimal places", "another currency");
  }

  @Test
  void nothingIsLoggedWithoutAnomaly(CapturedOutput output) {
    AnomalyReport.log(
        SESSION, new SessionCloses(List.of(), Set.of(), Set.of(), Set.of(), Set.of(), 3, 40));

    assertThat(output.getOut().lines().filter(l -> l.contains("AnomalyReport")))
        .as("unknown ISINs are counted in the collection log, not reported as anomalies")
        .isEmpty();
  }
}
