package dev.investai.market.application;

import dev.investai.market.domain.InstrumentId;
import dev.investai.market.domain.InstrumentRepository;
import dev.investai.market.domain.PeaEligibility;
import dev.investai.market.domain.PeaEligibilityStatus;
import dev.investai.shared.domain.ResourceNotFoundException;
import dev.investai.shared.logging.LogSanitizer;
import java.time.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The owner corrects the PEA eligibility of an instrument (spec 2.3, criterion 7): the correction
 * prevails over the rule and survives every reference data refresh.
 */
@Service
public class PeaEligibilityCorrection {

  private static final Logger LOG = LoggerFactory.getLogger(PeaEligibilityCorrection.class);

  private final InstrumentRepository instruments;
  private final Clock clock;

  /**
   * Creates the use case.
   *
   * @param instruments reference data
   * @param clock current time
   */
  public PeaEligibilityCorrection(InstrumentRepository instruments, Clock clock) {
    this.instruments = instruments;
    this.clock = clock;
  }

  /**
   * Stores the owner's answer; idempotent.
   *
   * @param id the instrument
   * @param status the owner's answer
   * @return the eligibility now in force
   * @throws ResourceNotFoundException {@code instrument.not-found} when unknown
   */
  @Transactional
  public PeaEligibility correct(InstrumentId id, PeaEligibilityStatus status) {
    if (!instruments.correctPeaEligibility(id, status, clock.instant())) {
      throw new ResourceNotFoundException(
          InstrumentTracking.NOT_FOUND, "Unknown instrument: " + id);
    }
    String instrument = LogSanitizer.oneLine(id);
    String eligibility = LogSanitizer.oneLine(status);
    LOG.info("PEA eligibility corrected: instrument={}, status={}", instrument, eligibility);
    return PeaEligibility.byOwner(status);
  }
}
