# Backend — consignes

Spring Boot 4.1 · Java 25 · Maven wrapper (`./mvnw`, ne pas utiliser un `mvn` global).

## Architecture (vérifiée par `ArchitectureTest`)

Un package par module métier sous `dev.investai` : `portfolio`, `market`, `assistant`, `news`,
`simulation` (+ `system`, `shared`). Chaque module est découpé en :

| Package | Contenu | Peut dépendre de |
|---|---|---|
| `domain` | Entités métier, value objects (records), règles, ports (interfaces) | JDK uniquement |
| `application` | Services de cas d'usage, orchestration, transactions | `domain` |
| `infrastructure` | Adaptateurs : JPA, clients HTTP, LLM (LangChain4j), schedulers | `domain`, `application` |
| `api` | Controllers REST (`*Controller`), DTO requête/réponse | `application`, `domain` |

- Pas de cycle entre modules. Un module en appelle un autre via son package `application`.
- Le `domain` ne connaît ni Spring, ni JPA, ni Jackson, ni LangChain4j.
- Les entités JPA vivent dans `infrastructure` et sont mappées vers le domaine ; jamais exposées
  par l'API.

## Règles de code

- Injection par constructeur uniquement. Classes de service `final` implicite via records ou
  champs `private final`.
- **Null-safety** : NullAway (mode JSpecify) est actif sur `dev.investai`. Tout est non-null par
  défaut ; annoter `@Nullable` (`org.jspecify.annotations`) explicitement sinon.
  Préférer `Optional` en retour plutôt que `null`.
- Records pour DTO et value objects. Validation Bean Validation (`@Valid`, `@NotNull`…) sur les DTO
  d'entrée.
- Erreurs (ADR 0006) : lever `ResourceNotFoundException` (404) ou `BusinessRuleException` (422)
  de `shared.domain` avec un `code` stable (`account.not-found`) ; `shared.api.ApiExceptionHandler`
  est le **seul** `@RestControllerAdvice` et produit le ProblemDetail (`code`, `errors`,
  `requestId`, jamais de `detail`). Le message de l'exception va dans les logs, jamais au client :
  uniquement des identifiants techniques (jamais IBAN, n° de compte, montant, e-mail, saisie
  utilisateur). Jamais de donnée sensible en variable de chemin (renvoyée dans `instance`).
- Configuration : dans `application.properties`, un séparateur de document `#---` doit être
  précédé d'une ligne vide (sinon il est ignoré en silence ; voir `ApplicationPropertiesTest`).
- Temps : injecter `java.time.Clock` (bean dans `shared.config`), jamais `Instant.now()` direct.
- Argent : `BigDecimal` + devise ; arrondis explicites (`RoundingMode.HALF_EVEN`).
- Logs : SLF4J (`private static final Logger LOG`), paramétrés (`LOG.info("x={}", x)`), aucune
  donnée sensible ; jamais l'URL brute ni la query string (utiliser la route, cf. `RequestIdFilter`).
- Javadoc sur les types publics de `src/main`.
- Limites Checkstyle : méthode ≤ 40 lignes, complexité cyclomatique ≤ 10, ≤ 6 paramètres.

## Sécurité (ADR 0007)

- API **fermée par défaut** : un nouvel endpoint public s'ajoute explicitement dans
  `auth.infrastructure.SecurityConfiguration`, avec un test qui prouve l'accès anonyme.
- Erreurs 401/403 : laisser Spring Security les produire (`ApiExceptionHandler` relance
  `AuthenticationException` / `AccessDeniedException`) ; codes `auth.*`.
- Identifiants : `INVESTAI_USERNAME` / `INVESTAI_PASSWORD_HASH` (PBKDF2,
  `scripts/hash-password.mjs`), jamais de mot de passe en clair hors de `FixtureCredentials` (tests).
- Ne jamais logger un nom d'utilisateur saisi, un mot de passe, un cookie ou un jeton.

## Persistance (ADR 0005)

- PostgreSQL 18. Le schéma appartient à **Flyway** : `src/main/resources/db/migration/V<n>__<description>.sql`.
  Hibernate est en `ddl-auto=validate` (ne jamais passer en `update`/`create`).
- **Ne jamais modifier une migration déjà mergée** : corriger via une nouvelle migration.
- Conventions SQL : `snake_case`, tables au singulier, clés `uuid` générées par l'application,
  montants `numeric(19, 4)` + devise `char(3)`, dates `timestamptz` (UTC).
- Entités JPA et repositories Spring Data uniquement dans `infrastructure` (ArchUnit), mappés
  vers/depuis le domaine. `open-in-view=false` : charger ce qu'il faut dans le service transactionnel.
- Aucun identifiant de connexion dans le code : `SPRING_DATASOURCE_*`, Docker Compose (profil
  `local`) ou Testcontainers (tests).

## Tests

- Unitaires : `*Test.java` (Surefire), JUnit 5 + AssertJ + Mockito. Domaine testé sans Spring.
- Intégration : `*IT.java` (Failsafe), annotés **`@IntegrationTest`** (`dev.investai.support`) :
  contexte complet + PostgreSQL Testcontainers + `MockMvcTester`. Ne pas créer d'autre
  combinaison d'annotations (`@SpringBootTest` seul, `@MockitoBean`…) sans raison : chaque variante
  crée un nouveau contexte et un nouveau conteneur, ce qui ralentit la suite.
- Repositories : tester via `@IntegrationTest` (vraie base), jamais avec H2.
- Règles ArchUnit : toute nouvelle règle qui pourrait passer « à vide » (aucune classe ciblée) doit
  être prouvée par un test sur des fixtures fautives (`ArchitectureRulesTest`, `architecture.fixture`).
- Répertoire de travail = `backend/` pour Maven, les tests et `spring-boot:run` (chemins relatifs
  vers `../infra`). Configurer l'IDE en conséquence.
- Web : **`@SecuredWebMvcTest(MonController.class)`** + `MockMvcTester` (vraie config de sécurité) ;
  utilisateur via `with(user("…"))` ou `@WithMockUser`, requêtes modifiantes avec `with(csrf().asHeader())` (jeton accepté uniquement en en-tête).
- `@IntegrationTest` démarre un vrai serveur (port aléatoire) : `@LocalServerPort` + `HttpClient`
  pour ce que MockMvc ne voit pas (cookies, `/error`, pare-feu HTTP, en-têtes du conteneur).
- Nommage : `methodOrBehaviour_condition_expectedResult` ou phrase descriptive en camelCase.
- Mutation testing (PIT) : `./mvnw -Pmutation test-compile org.pitest:pitest-maven:mutationCoverage`,
  seuil 70 %, exécuté chaque nuit.

## Pièges connus

Format : symptôme → cause → bonne pratique. Compléter à chaque étape (workflow, étape 7).

- **Spring Boot 4** : `server.error.*` sans effet → clés renommées → `spring.web.error.*`.
- **Échec de démarrage qui affiche un secret** : les analyseurs de Boot impriment la valeur
  rejetée d'une propriété (Bean Validation, conversion) → valider une valeur sensible dans le
  `@Bean` qui l'utilise, avec un message sans la valeur (cf. `SingleUserConfiguration`).
- **Test MockMvc en 403 sur un POST** : `csrf()` envoie le jeton en paramètre `_csrf`, refusé
  ici → `with(csrf().asHeader())`.
- **MockMvc ne voit pas** les cookies de session (posés par Tomcat), `/error`, le pare-feu HTTP
  ni les en-têtes ajoutés par le conteneur → `@IntegrationTest` + `HttpClient` (cf. `AuthApiIT`).
- **Checkstyle « Hard-coded secret »** : attrape toute constante `password|secret|token = "…"`,
  tests compris → générer la valeur (`UUID.randomUUID()`) ; vecteur de test public :
  `FixtureCredentials.PASSPHRASE` (décision du propriétaire, pas de suppression dans `config/`).
- **Checkstyle ClassFanOutComplexity (> 20)** : config Spring Security ou advice qui grossit →
  découper par responsabilité (`SecurityConfiguration` / `SingleUserConfiguration` /
  `HttpFirewallConfiguration`, `FieldViolation`).
- **PMD** : `GuardLogStatement` → calculer les arguments de log dans des variables locales ;
  `AvoidDuplicateLiterals` → constantes ; `UnitTestShouldIncludeAssert` ne voit pas les
  assertions dans un lambda → récupérer la valeur (`AtomicReference`) puis asserter dehors ;
  `TestClassWithoutTestCases` sur une classe de test nommée `Test…` → la renommer
  (`FixtureCredentials`) ; `AvoidUsingHardCodedIP` → asserter la propriété (`isLoopbackAddress`).
- **SpotBugs / FindSecBugs** : `CRLF_INJECTION_LOGS` → `LogSanitizer.oneLine` ;
  `CT_CONSTRUCTOR_THROW` → valider avant `super()` et garder la classe `final` ;
  `SERVLET_HEADER` → ne pas lire d'en-tête soi-même, réutiliser l'API Spring ;
  `SPRING_CSRF_UNRESTRICTED_REQUEST_MAPPING` → séparer méthodes sûres et modifiantes ;
  `THROWS_METHOD_THROWS_RUNTIMEEXCEPTION` → typer précisément l'exception relancée.
- **NullAway** ne comprend pas `assertThat(x).isNotNull()` → `Objects.requireNonNull(x)`.
- **Hook de formatage** (après chaque édition) : retire un import encore inutilisé → ajouter
  l'import en même temps que son usage, ou relancer `./mvnw spotless:apply` puis compiler.
- **`@WebMvcTest` avec contrôleur imbriqué** : exclu du scan → `@Import(LeController.class)`.
- **CodeQL** signale `case X _` (variable non lue) et bloque le merge (fil de revue à
  résoudre) → un handler explicite par type plutôt qu'un `switch` à motifs anonymes.

## Outils qualité (tous bloquants dans `./mvnw verify`)

Spotless (google-java-format), Checkstyle (`config/checkstyle`), Error Prone + NullAway (compilation,
warnings = erreurs), PMD (`config/pmd`), SpotBugs + FindSecBugs (`config/spotbugs`), ArchUnit,
JaCoCo (80 % lignes / 70 % branches), Maven Enforcer (Java 25, dépendances bannies).

Toute exclusion dans `config/**` doit être justifiée par un commentaire et validée par l'utilisateur.
