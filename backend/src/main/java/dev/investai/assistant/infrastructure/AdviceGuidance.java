package dev.investai.assistant.infrastructure;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/**
 * The advice guidance served to the model (spec 3.1, criteria 32 to 35), versioned in the
 * repository and loaded at startup: the instructions of the server (rules of every exchange) and
 * the text of the prompt {@code conseil-placement} (the whole method). No data is ever inserted in
 * them: what comes from the database travels in the outputs of the tools.
 */
@Component
public final class AdviceGuidance {

  /** Instructions of the server, sent in the answer to {@code initialize}. */
  static final String SERVER_INSTRUCTIONS = "assistant/server-instructions.txt";

  /** Text of the prompt {@code conseil-placement}. */
  static final String ADVICE_PROMPT = "assistant/prompt-conseil-placement.txt";

  private final String serverInstructions;
  private final String advicePrompt;

  /** Loads the texts; a missing file stops the startup. */
  public AdviceGuidance() {
    this.serverInstructions = load(SERVER_INSTRUCTIONS);
    this.advicePrompt = load(ADVICE_PROMPT);
  }

  /**
   * Returns the instructions of the server.
   *
   * @return the French text
   */
  public String serverInstructions() {
    return serverInstructions;
  }

  /**
   * Returns the text of the prompt {@code conseil-placement}.
   *
   * @return the French text
   */
  public String advicePrompt() {
    return advicePrompt;
  }

  private static String load(String path) {
    try {
      return new ClassPathResource(path).getContentAsString(StandardCharsets.UTF_8).strip();
    } catch (IOException e) {
      throw new UncheckedIOException("Advice guidance not found: " + path, e);
    }
  }
}
