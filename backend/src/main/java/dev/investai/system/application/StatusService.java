package dev.investai.system.application;

import java.time.Clock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/** Reports the runtime status of the application. */
@Service
public class StatusService {

  private final Clock clock;
  private final String applicationName;

  /**
   * Creates the service.
   *
   * @param clock clock used to timestamp the status
   * @param applicationName logical application name
   */
  public StatusService(Clock clock, @Value("${spring.application.name}") String applicationName) {
    this.clock = clock;
    this.applicationName = applicationName;
  }

  /**
   * Returns the current application status.
   *
   * @return a snapshot of the application status
   */
  public ApplicationStatus currentStatus() {
    return new ApplicationStatus(applicationName, clock.instant());
  }
}
