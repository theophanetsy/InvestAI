package dev.investai.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noFields;
import static com.tngtech.archunit.library.GeneralCodingRules.NO_CLASSES_SHOULD_ACCESS_STANDARD_STREAMS;
import static com.tngtech.archunit.library.GeneralCodingRules.NO_CLASSES_SHOULD_THROW_GENERIC_EXCEPTIONS;
import static com.tngtech.archunit.library.GeneralCodingRules.NO_CLASSES_SHOULD_USE_JAVA_UTIL_LOGGING;
import static com.tngtech.archunit.library.GeneralCodingRules.NO_CLASSES_SHOULD_USE_JODATIME;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RestController;

/**
 * Architecture rules. Each business module ({@code dev.investai.<module>}) is split into {@code
 * domain}, {@code application}, {@code infrastructure} and {@code api} packages.
 */
// ArchUnit discovers @ArchTest fields, which PMD does not recognise as test cases.
@SuppressWarnings("PMD.TestClassWithoutTestCases")
@AnalyzeClasses(packages = "dev.investai", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

  static final String DOMAIN = "..domain..";
  static final String APPLICATION = "..application..";
  static final String INFRASTRUCTURE = "..infrastructure..";
  static final String API = "..api..";

  @ArchTest
  static final ArchRule DOMAIN_IS_FRAMEWORK_FREE =
      noClasses()
          .that()
          .resideInAPackage(DOMAIN)
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage(
              "org.springframework..",
              "jakarta.persistence..",
              "tools.jackson..",
              "com.fasterxml.jackson..",
              "dev.langchain4j..")
          .because("the domain model must stay pure Java and testable in isolation");

  @ArchTest
  static final ArchRule DOMAIN_DOES_NOT_DEPEND_ON_OUTER_LAYERS =
      noClasses()
          .that()
          .resideInAPackage(DOMAIN)
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage(APPLICATION, INFRASTRUCTURE, API);

  @ArchTest
  static final ArchRule APPLICATION_DOES_NOT_DEPEND_ON_ADAPTERS =
      noClasses()
          .that()
          .resideInAPackage(APPLICATION)
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage(INFRASTRUCTURE, API);

  @ArchTest
  static final ArchRule API_DOES_NOT_TOUCH_INFRASTRUCTURE =
      noClasses()
          .that()
          .resideInAPackage(API)
          .should()
          .dependOnClassesThat()
          .resideInAPackage(INFRASTRUCTURE)
          .because("controllers go through application services");

  @ArchTest
  static final ArchRule CONTROLLERS_LIVE_IN_API =
      classes()
          .that()
          .areAnnotatedWith(RestController.class)
          .should()
          .resideInAPackage(API)
          .andShould()
          .haveSimpleNameEndingWith("Controller");

  @ArchTest
  static final ArchRule NO_CYCLES_BETWEEN_MODULES =
      slices().matching("dev.investai.(*)..").should().beFreeOfCycles();

  @ArchTest
  static final ArchRule NO_FIELD_INJECTION =
      noFields().should().beAnnotatedWith(Autowired.class).because("use constructor injection");

  @ArchTest static final ArchRule NO_STANDARD_STREAMS = NO_CLASSES_SHOULD_ACCESS_STANDARD_STREAMS;

  @ArchTest
  static final ArchRule NO_GENERIC_EXCEPTIONS = NO_CLASSES_SHOULD_THROW_GENERIC_EXCEPTIONS;

  @ArchTest static final ArchRule NO_JUL = NO_CLASSES_SHOULD_USE_JAVA_UTIL_LOGGING;

  @ArchTest static final ArchRule NO_JODATIME = NO_CLASSES_SHOULD_USE_JODATIME;
}
