package dev.investai.system.api;

import dev.investai.system.application.ApplicationStatus;
import java.time.Instant;

/**
 * Status payload returned by {@link StatusController}.
 *
 * @param application logical application name
 * @param serverTime server time in UTC
 */
public record StatusResponse(String application, Instant serverTime) {

  static StatusResponse from(ApplicationStatus status) {
    return new StatusResponse(status.application(), status.serverTime());
  }
}
