package dev.investai.market.api;

import dev.investai.market.application.PeaEligibilityCorrection;
import dev.investai.market.domain.InstrumentId;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The owner's correction of the PEA eligibility of an instrument (spec 2.3, criterion 7). Reading
 * the eligibility goes through {@link MarketController} (search and details).
 */
@RestController
@Tag(name = "market")
@RequestMapping("/api/v1/market/instruments")
public class PeaEligibilityController {

  private final PeaEligibilityCorrection correction;

  /**
   * Creates the controller.
   *
   * @param correction corrects the PEA eligibility
   */
  public PeaEligibilityController(PeaEligibilityCorrection correction) {
    this.correction = correction;
  }

  /**
   * Corrects the PEA eligibility of an instrument (idempotent): the owner's answer prevails over
   * the rule and is never overwritten by a reference data refresh.
   *
   * @param id the instrument; 404 {@code instrument.not-found} when unknown
   * @param request the owner's answer
   * @return the eligibility now in force, with source {@code OWNER}
   */
  @PutMapping("/{id}/pea-eligibility")
  public PeaEligibilityResponse correctPeaEligibility(
      @PathVariable UUID id, @Valid @RequestBody PeaEligibilityRequest request) {
    return PeaEligibilityResponse.from(correction.correct(new InstrumentId(id), request.status()));
  }
}
