package dev.investai;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** InvestAI backend entry point. */
@SpringBootApplication
public class InvestaiBackendApplication {

  /**
   * Starts the application.
   *
   * @param args command-line arguments
   */
  public static void main(String[] args) {
    SpringApplication.run(InvestaiBackendApplication.class, args);
  }
}
