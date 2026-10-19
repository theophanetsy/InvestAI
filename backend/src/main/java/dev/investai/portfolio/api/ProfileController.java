package dev.investai.portfolio.api;

import dev.investai.portfolio.application.InvestorProfiles;
import dev.investai.portfolio.domain.RiskProfile;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.Arrays;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** The investor profile (spec 2.3, criteria 1 and 2), part of the {@code portfolio} module. */
@RestController
@Tag(name = "portfolio")
@RequestMapping("/api/v1/profile")
public class ProfileController {

  private final InvestorProfiles profiles;

  /**
   * Creates the controller.
   *
   * @param profiles the use cases
   */
  public ProfileController(InvestorProfiles profiles) {
    this.profiles = profiles;
  }

  /**
   * Returns the investor profile.
   *
   * @return the profile; 404 {@code profile.not-found} until it is filled in
   */
  @GetMapping
  public ProfileResponse getProfile() {
    return ProfileResponse.from(profiles.get());
  }

  /**
   * Creates or replaces the investor profile (idempotent).
   *
   * @param request the profile; 400 {@code validation.failed} with the rejected fields
   * @return the stored profile
   */
  @PutMapping
  public ProfileResponse saveProfile(@Valid @RequestBody ProfileRequest request) {
    return ProfileResponse.from(profiles.save(request.toChange()));
  }

  /**
   * Lists the four risk profiles with their French label and description.
   *
   * @return the profiles, from the most prudent to the most aggressive
   */
  @GetMapping("/risk-profiles")
  public List<RiskProfileResponse> listRiskProfiles() {
    return Arrays.stream(RiskProfile.values()).map(RiskProfileResponse::from).toList();
  }
}
