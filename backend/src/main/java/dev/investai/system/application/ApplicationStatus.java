package dev.investai.system.application;

import java.time.Instant;

/**
 * Snapshot of the application status.
 *
 * @param application logical application name
 * @param serverTime server time when the snapshot was taken
 */
public record ApplicationStatus(String application, Instant serverTime) {}
