package dev.investai.auth.infrastructure;

import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.configuration.WebSecurityCustomizer;
import org.springframework.security.web.firewall.StrictHttpFirewall;

/**
 * Rejects requests whose {@code Host} is not an expected name (400 problem, through {@code
 * /error}). Protects the locally running app from DNS rebinding: a web page whose domain is
 * re-pointed to 127.0.0.1 would otherwise be same-origin with the API.
 */
@Configuration(proxyBeanMethods = false)
public class HttpFirewallConfiguration {

  /**
   * Strict firewall (Spring Security's default) restricted to the configured host names.
   *
   * @param allowedHostnames {@code investai.security.allowed-hostnames}
   * @return the customizer installing the firewall
   */
  @Bean
  WebSecurityCustomizer hostRestrictedFirewall(
      @Value("${investai.security.allowed-hostnames}") Set<String> allowedHostnames) {
    StrictHttpFirewall firewall = new StrictHttpFirewall();
    firewall.setAllowedHostnames(allowedHostnames::contains);
    return web -> web.httpFirewall(firewall);
  }
}
