package dev.investai.support;

import dev.investai.auth.infrastructure.SecurityConfiguration;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.annotation.AliasFor;
import org.springframework.test.context.TestPropertySource;

/**
 * {@code @WebMvcTest} slice running behind the real security configuration (ADR-0007): anonymous
 * requests are rejected unless public, and modifying requests need a CSRF token ({@code
 * with(csrf().asHeader())}). Use it instead of a bare {@code @WebMvcTest}.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@WebMvcTest
@Import(SecurityConfiguration.class)
@TestPropertySource(
    properties = {FixtureCredentials.USERNAME_PROPERTY, FixtureCredentials.PASSWORD_HASH_PROPERTY})
public @interface SecuredWebMvcTest {

  /**
   * Controllers under test.
   *
   * @return the controller classes
   */
  @AliasFor(annotation = WebMvcTest.class, attribute = "controllers")
  Class<?>[] value() default {};
}
