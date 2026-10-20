package dev.investai.architecture;

import static com.tngtech.archunit.base.DescribedPredicate.not;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAnyPackage;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.simpleName;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.simpleNameEndingWith;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.type;
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
import dev.investai.market.application.CallBudget;
import dev.investai.market.application.DailyCollection;
import dev.investai.market.application.EodhdMaintenance;
import dev.investai.market.application.IndicatorComputation;
import dev.investai.market.application.InstrumentTracking;
import dev.investai.market.application.PeaEligibilityCorrection;
import dev.investai.market.application.PriceHistoryLoader;
import dev.investai.market.application.PriceRecorder;
import dev.investai.market.application.ReferenceDataLoader;
import dev.investai.portfolio.application.InvestorProfiles;
import dev.investai.portfolio.application.PortfolioCommands;
import dev.investai.portfolio.application.ProfileChange;
import jakarta.persistence.Embeddable;
import jakarta.persistence.Entity;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.repository.Repository;
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
  static final String JPA = "jakarta.persistence..";
  static final String MCP_SDK = "io.modelcontextprotocol..";
  static final String SPRING_AI = "org.springframework.ai..";

  @ArchTest
  static final ArchRule ENTITIES_LIVE_IN_INFRASTRUCTURE =
      classes()
          .that()
          .areAnnotatedWith(Entity.class)
          .or()
          .areAnnotatedWith(Embeddable.class)
          .should()
          .resideInAPackage(INFRASTRUCTURE)
          .because("JPA entities are persistence details, mapped to/from the domain model");

  @ArchTest
  static final ArchRule REPOSITORIES_LIVE_IN_INFRASTRUCTURE =
      classes()
          .that()
          .areAssignableTo(Repository.class)
          .should()
          .resideInAPackage(INFRASTRUCTURE)
          .because("application services depend on domain ports, implemented by adapters");

  @ArchTest
  static final ArchRule ONLY_INFRASTRUCTURE_USES_JPA =
      noClasses()
          .that()
          .resideInAnyPackage(API, APPLICATION)
          .should()
          .dependOnClassesThat()
          .resideInAPackage(JPA)
          .because("entities must never leak into use cases or the HTTP API");

  @ArchTest
  static final ArchRule DOMAIN_IS_FRAMEWORK_FREE =
      noClasses()
          .that()
          .resideInAPackage(DOMAIN)
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage(
              "org.springframework..",
              JPA,
              "tools.jackson..",
              "com.fasterxml.jackson..",
              "dev.langchain4j..",
              MCP_SDK)
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

  /**
   * The portfolio calls the market module through its application services and uses its domain
   * types; from its API, only the DTOs shared to keep one type in the generated client: the
   * instrument and its PEA eligibility (phase 2.3), its indicators (phase 2.4).
   */
  @ArchTest
  static final ArchRule PORTFOLIO_USES_MARKET_THROUGH_ITS_APPLICATION =
      noClasses()
          .that()
          .resideInAPackage("dev.investai.portfolio..")
          .should()
          .dependOnClassesThat(
              resideInAPackage("dev.investai.market.infrastructure..")
                  .or(
                      resideInAPackage("dev.investai.market.api..")
                          .and(not(simpleName("InstrumentSummaryResponse")))
                          .and(not(simpleName("PeaEligibilityResponse")))
                          .and(not(simpleName("IndicatorResponse")))
                          .and(not(simpleName("IndicatorsResponse")))))
          .because("a module calls another one through its application package");

  /**
   * The MCP library (ADR-0010) is an adapter detail, like JPA: tools live in {@code infrastructure}
   * and call use cases.
   */
  @ArchTest
  static final ArchRule ONLY_INFRASTRUCTURE_USES_MCP =
      noClasses()
          .that()
          .resideOutsideOfPackage(INFRASTRUCTURE)
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage(MCP_SDK, SPRING_AI)
          .because("the MCP library is confined to adapters (ADR-0010)");

  /**
   * The assistant (MCP tools, phase 3) reads the other modules through their use cases only: never
   * their repositories (domain ports), adapters or controllers. The shared kernel ({@code
   * shared.api.RequestIdFilter}, logging) is allowed.
   */
  @ArchTest
  static final ArchRule ASSISTANT_USES_OTHER_MODULES_THROUGH_THEIR_APPLICATION =
      noClasses()
          .that()
          .resideInAPackage("dev.investai.assistant..")
          .should()
          .dependOnClassesThat(
              not(resideInAnyPackage("dev.investai.assistant..", "dev.investai.shared.."))
                  .and(
                      resideInAnyPackage("dev.investai.*.infrastructure..", "dev.investai.*.api..")
                          .or(
                              resideInAPackage("dev.investai..")
                                  .and(simpleNameEndingWith("Repository")))))
          .because("MCP tools call use cases, never repositories (ADR-0010)");

  /**
   * The advice tools are read only (spec 3.1, criterion 1): the assistant never calls a use case
   * that writes, nor one that would call a provider (tracking an instrument triggers EODHD).
   */
  @ArchTest
  static final ArchRule ASSISTANT_NEVER_CALLS_A_WRITE_USE_CASE =
      noClasses()
          .that()
          .resideInAPackage("dev.investai.assistant..")
          .should()
          .dependOnClassesThat(
              type(PortfolioCommands.class)
                  .or(type(InstrumentTracking.class))
                  .or(type(PeaEligibilityCorrection.class))
                  .or(type(IndicatorComputation.class))
                  .or(type(DailyCollection.class))
                  .or(type(EodhdMaintenance.class))
                  .or(type(PriceHistoryLoader.class))
                  .or(type(ReferenceDataLoader.class))
                  .or(type(PriceRecorder.class))
                  .or(type(CallBudget.class)))
          .orShould()
          .callMethod(InvestorProfiles.class, "save", ProfileChange.class)
          .because("the MCP tools of phase 3 are read only (ADR-0010, spec 3.1)");

  @ArchTest
  static final ArchRule NO_FIELD_INJECTION =
      noFields().should().beAnnotatedWith(Autowired.class).because("use constructor injection");

  @ArchTest static final ArchRule NO_STANDARD_STREAMS = NO_CLASSES_SHOULD_ACCESS_STANDARD_STREAMS;

  @ArchTest
  static final ArchRule NO_GENERIC_EXCEPTIONS = NO_CLASSES_SHOULD_THROW_GENERIC_EXCEPTIONS;

  @ArchTest static final ArchRule NO_JUL = NO_CLASSES_SHOULD_USE_JAVA_UTIL_LOGGING;

  @ArchTest static final ArchRule NO_JODATIME = NO_CLASSES_SHOULD_USE_JODATIME;
}
