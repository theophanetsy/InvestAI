package dev.investai.system.api;

import dev.investai.system.application.StatusService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Public status endpoint used by the frontend and smoke tests. */
@RestController
@RequestMapping("/api/v1/status")
public class StatusController {

  private final StatusService statusService;

  /**
   * Creates the controller.
   *
   * @param statusService service providing the status
   */
  public StatusController(StatusService statusService) {
    this.statusService = statusService;
  }

  /**
   * Returns the application status.
   *
   * @return the status response
   */
  @GetMapping
  public StatusResponse status() {
    return StatusResponse.from(statusService.currentStatus());
  }
}
