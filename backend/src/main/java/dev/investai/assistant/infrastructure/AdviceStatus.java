package dev.investai.assistant.infrastructure;

/** Whether a tool that needs the investor profile could answer (spec 3.1, criteria 14 and 31). */
public enum AdviceStatus {
  /** The profile is filled in: the result is complete. */
  OK,
  /** The owner has not filled the profile in yet: nothing else is returned (not an error). */
  PROFILE_MISSING
}
