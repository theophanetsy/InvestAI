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

## Contrat OpenAPI (ADR 0004)

- springdoc décrit `/api/**` ; le contrat est versionné dans `api/openapi.json`. Après un
  changement d'API : `./mvnw verify` puis `npm run api:update` (racine), et committer
  `api/openapi.json` + `frontend/src/app/api/generated/` avec le code.
- `operationId` = nom de la méthode du contrôleur : le choisir pour le client TypeScript
  (`getStatus`, pas `status`), unique dans toute l'API. Un `@Tag(name = "<module>")` par contrôleur.
- Champs des DTO : `required` par défaut, optionnels seulement si `@Nullable`
  (`RequiredUnlessNullableConverter`) ; un champ nul est absent du JSON.
- Les erreurs sont déclarées globalement (schéma `Problem`, `OpenApiConfiguration`) : ne pas
  ajouter d'`@ApiResponse` d'erreur par endpoint. Un endpoint servi par un filtre (ex. logout)
  s'ajoute au contrat par un `OpenApiCustomizer` (cf. `LogoutOpenApiConfiguration`) annoté
  `@Order(HIGHEST_PRECEDENCE)` : il doit passer avant `problemResponses` (dernier), qui ajoute la
  réponse d'erreur à toutes les opérations.
- Nouveau module exposé dans l'API : ajouter son tag à la liste fermée de `OpenApiContractIT`
  (voulu : un tag inattendu échoue).
- Le build backend lit `../api/openapi.json` et ses tests `../frontend` : un futur `Dockerfile`
  doit avoir la racine du dépôt comme contexte.

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

## Module `market` (Phases 2.1 et 2.2, ADR-0009)

- **Seuls les jobs** (`market.infrastructure.MarketJobs`) appellent les fournisseurs (Euronext,
  EODHD) ; l'API, et plus tard le dashboard, les simulations et les tools de l'agent, **lisent la
  base** (`MarketQueries`). Aucun endpoint ne déclenche un appel externe (suivre une valeur ne fait
  que poser `tracked_since`, l'historique arrive au passage suivant du job EODHD).
- Collecte Euronext : toutes les heures de 9 h à 23 h (Paris) **et** 1 min après
  `ApplicationReadyEvent` (`MarketJobs.collectShortlyAfterStartup`, même méthode que le cron,
  planifiée sur le `TaskScheduler`, jamais exécutée dans le thread de démarrage). Idempotente :
  aucun téléchargement si la séance est déjà complète.
- Jobs coupés par `investai.market.jobs.enabled=false` : c'est le cas dans **tout**
  `@IntegrationTest` (avec `investai.market.eodhd.api-key=` vide) et dans `npm run stack:start`.
  Les tests déclenchent les cas d'usage eux-mêmes, avec de faux fournisseurs (`FakeTradeFiles`,
  `FakeEodhd`, `MutableClock`) et les vrais adaptateurs : construire le service à la main
  (`MaintenanceFactory`) plutôt que `@MockitoBean` (nouveau contexte).
- Identifiant pivot **`Listing` (ISIN + MIC)** ; le symbole EODHD (`AI.PA`) est calculé dans
  l'adaptateur (`EodhdSymbols.symbolOf`). Règle MIC de Paris : code EODHD `AL…` → `ALXP`, sinon `XPAR`.
- Prix : `BigDecimal` lu depuis le texte, ramené à 4 décimales **sans arrondi** par `Prices.exact`
  (vide si impossible) ; `DailyClose` refuse tout le reste. Clôtures brutes (`close`), jamais
  `adjusted_close`. Une clôture `EURONEXT` n'est jamais remplacée par `EODHD` (`PriceMerge`, et
  aussi la clause `WHERE` de l'upsert SQL).
- Persistance en **`JdbcClient`** (pas d'entité JPA) : upserts `ON CONFLICT` idempotents en lot,
  réservation atomique du budget (`INSERT … ON CONFLICT … WHERE calls < :limit RETURNING`).
  `timestamptz` lié en `OffsetDateTime` (`Timestamps`) : le driver PostgreSQL ne lie pas `Instant`.
- Fuseaux : séances en `Europe/Paris` (`EuronextCalendar.ZONE`), budget EODHD en
  jours **UTC** (comme le quota).
- **Budget EODHD** : `CallBudget.tryReserve(...)` **avant** chaque appel ; ordre de priorité dans
  `EodhdMaintenance` (rattrapage, historique, référentiel). Un échec arrête le passage et reporte
  la valeur (ou le référentiel) **au lendemain UTC** : une erreur permanente (valeur radiée) ne doit
  ni consommer tout le budget ni bloquer les autres ; le travail reste en file (`price_catch_up`,
  `history_loaded = false`).
- **Séance complète** = ses deux fichiers collectés, ou rattrapée par EODHD (`segment` nul). Une
  séance à un seul fichier est rattrapée comme une séance manquée (`CollectionJournal.catchUpStart`).
- **Les deux jobs peuvent tourner en même temps** (planificateur sur threads virtuels,
  `spring.threads.virtual.enabled=true`) : un verrou par cas d'usage ne les sérialise pas entre
  eux → transitions d'état sûres en base (`CatchUpQueue.remove` ne supprime qu'une entrée non
  élargie entre-temps).
- **Clé EODHD** : uniquement `INVESTAI_EODHD_API_KEY`, ajoutée sur la requête envoyée par
  `ApiTokenInterceptor` (l'URI connue de `RestClient` ne la contient jamais) ; tout appel passe par
  `HttpCalls.call`, qui ne garde ni message Spring, ni corps, ni cause (`RedactedCause` conserve la
  pile sans le message). Test de non-régression : `EodhdKeyLeakTest`.
- **Tout Paris (Phase 2.2, [spec](../docs/specs/phase-2-02-all-paris-closes.md))** : la collecte
  Euronext calcule la clôture de **chaque instrument en base** (`InstrumentRepository.findAll`),
  suivi ou non ; un ISIN du fichier absent du référentiel est ignoré et compté
  (`unknownIsins`/`unknownTrades` dans le log de fin de fichier). **« Suivi » ne pilote plus que
  EODHD** : 1 an d'historique et rattrapage des séances manquées (`findTracked`). Limite assumée :
  une valeur non suivie n'a que les séances collectées depuis la mise en service, **avec des
  trous** si l'application n'a pas tourné le jour de publication du fichier (rien ne les comble,
  ne jamais interpoler). Suivre une valeur charge quand même son année d'historique, sans
  remplacer ses clôtures `EURONEXT`.
- **Activité de séance** (`V3__daily_price_activity.sql`) : `daily_price.volume` (`bigint`) et
  `trade_count` (`integer`), **nullables** = inconnus. Euronext : transactions retenues pour la
  clôture (mécanisme 1, lieu de cotation, devise de l'instrument), volume = somme des
  `MifidQuantity` ; une quantité illisible, fractionnaire ou un dépassement rend le volume nul sans
  toucher la clôture ni le nombre de transactions (`Volumes.units`, jamais d'arrondi). EODHD :
  `volume` lu en texte (un volume bizarre ne fait pas échouer l'appel), `trade_count` toujours nul.
  Les définitions diffèrent selon `source` : ne pas comparer des volumes de sources différentes.
  `PriceMerge` et l'upsert déplacent volume et nombre de transactions avec la clôture.
- **Anomalies** (`AnomalyReport`) : une ligne par type et par fichier (nombre + 10 cotations
  au plus), plus une ligne par valeur (~1 300 valeurs).
- **Performance** (`ParisFilePerformanceIT`, fichier synthétique de 483 000 transactions) :
  ~2,5 s par fichier, dominé par la lecture ; l'upsert des 1 300 clôtures prend ~0,15 s. Le test
  échoue au-delà de 2 min (régression grossière seulement) ; détail dans la spec 2.2.
- **Données de test synthétiques** uniquement (`MifirFixtures`, JSON écrit à la main) : publier
  des fichiers Euronext ou des réponses EODHD réels serait une redistribution. Un fichier réel ne
  sert qu'à valider le lecteur **hors du dépôt** (scratchpad), test temporaire jamais commité.

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
- **springdoc** : sans `springdoc.paths-to-match`, `/error` entre dans le contrat avec des
  `operationId` suffixés `_1` ; sans `default-produces-media-type`, les réponses sont en `*/*` ;
  springdoc ignore JSpecify (d'où `RequiredUnlessNullableConverter`).
- **`null` absent d'un corps JSON sortant** : `spring.jackson.default-property-inclusion=non_null`
  est global, il s'applique aussi aux `RestClient` de Boot → si une API tierce exige un `null`
  explicite, `@JsonInclude(ALWAYS)` sur le DTO concerné.
- **AssertJ + Jackson** : `JsonNode` est `Iterable` → `assertThat(node)` fait une assertion de
  collection ; comparer `node.asString()` ou des `JsonNode` entiers.
- **Échec de démarrage qui affiche un secret** : les analyseurs de Boot impriment la valeur
  rejetée d'une propriété (Bean Validation, conversion) → valider une valeur sensible dans le
  `@Bean` qui l'utilise, avec un message sans la valeur (cf. `SingleUserConfiguration`).
- **Test MockMvc en 403 sur un POST** : `csrf()` envoie le jeton en paramètre `_csrf`, refusé
  ici → `with(csrf().asHeader())`.
- **`AuthApiIT` cassé selon l'ordre des IT (session créée, cookie `XSRF-TOKEN` absent)** :
  `csrf()` de spring-security-test remplace durablement le dépôt CSRF du filtre partagé par un
  dépôt en session → dans un `@IntegrationTest`, jamais `csrf()` : GET pour obtenir le cookie
  `XSRF-TOKEN`, puis le renvoyer en cookie + en-tête `X-XSRF-TOKEN` (cf. `OpenApiContractIT`).
  Reproduire l'ordre de la CI : `./mvnw verify -Dfailsafe.runOrder=reversealphabetical`.
- **OSV-Scanner sur une dépendance transitive** (ex. Jackson 2 via springdoc/swagger-core) :
  version gérée par le BOM de Boot → surcharger la propriété (`jackson-2-bom.version`) dans le
  bloc « Security overrides » du `pom.xml`, avec les identifiants GHSA.
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
- **Clé d'API dans un message d'exception** : `ResourceAccessException` (« I/O error on GET request
  for "<url complète>" ») et les erreurs du client HTTP du JDK recopient l'URL, query string
  comprise → clé ajoutée par un intercepteur (`ApiTokenInterceptor`), erreurs converties par
  `HttpCalls` sans message ni cause d'origine ; PMD `PreserveStackTrace` → passer
  `RedactedCause.of(ex)`, pas `ex`.
- **Serveur HTTP de test** : `com.sun.net.httpserver` interdit (Checkstyle `IllegalImport`
  `com.sun`, tests compris) → `MockRestServiceServer.bindTo(RestClient.Builder)` ; l'adaptateur
  prend un `RestClient.Builder` en paramètre (`EodhdClient.create`).
- **Pas de `RestClient.Builder` auto-configuré** (module `spring-boot-restclient` absent, ne pas
  l'ajouter) → `RestClient.builder()` + `JdkClientHttpRequestFactory` ; le `HttpClient` du JDK est
  un `@Bean` (fermé par Spring, sinon PMD `CloseResource`), sans redirection.
- **FindSecBugs `IMPROPER_UNICODE`** sur `java.text.Normalizer` → normaliser en SQL
  (`unaccent(lower(...))`, extension créée par Flyway).
- **FindSecBugs `CRLF_INJECTION_LOGS`** sur tout argument de log non constant (chaîne, enum,
  `LocalDate`, identifiant) → variable locale `LogSanitizer.oneLine(...)` avant l'appel (règle aussi
  PMD `GuardLogStatement`). PMD prend toute méthode nommée `warn`/`info` pour un logger → nommer
  autrement les méthodes utilitaires (`logEach`).
- **Record avec constantes de son propre type** (`Mic.XPAR`) : un `static final Pattern` déclaré
  après (ordre imposé par Checkstyle `DeclarationOrder`) vaut `null` quand le constructeur des
  constantes s'exécute → `NoClassDefFoundError` → valider sans champ statique non constant.
- **Error Prone** (avertissement = erreur) : `LoopOverCharArray` (`toCharArray` en boucle),
  `OperatorPrecedence` (`a && b || c`), `EnumOrdinal`, `TimeInStaticInitializer`
  (`LocalDate.now()` en `static final` de test) → `charAt`, méthodes intermédiaires, `EnumMap`,
  champ d'instance.
- **Checkstyle** `BooleanExpressionComplexity` (> 3 opérateurs) et `ClassFanOutComplexity` des
  `*IT` → extraire des méthodes ou des fabriques de test (`MaintenanceFactory`) ; règle « Hard-coded
  secret » déclenchée par `"api_token=" + key + "…"` dans un test → `String.join("=", "api_token", key)`.
- **PMD `UnitTestShouldIncludeAssert`** ne voit pas `then(mock).should()` (BDDMockito) →
  `Mockito.verify(...)` quand le test n'a pas d'autre assertion.
- **Clé EODHD visible par une instrumentation du client HTTP du JDK** : l'intercepteur la cache à
  `RestClient`, pas à la requête envoyée → en Phase 3 (OpenTelemetry), masquer `api_token` dans
  `url.full` ou désactiver l'instrumentation `java-http-client`, avec un test sur un span exporté ;
  ne jamais activer `-Djdk.httpclient.HttpClient.log`.
- **`@Scheduled` en parallèle** : avec les threads virtuels, Boot fournit un
  `SimpleAsyncTaskScheduler` → deux tâches planifiées s'exécutent simultanément (pas de thread
  unique) → protéger chaque cas d'usage et rendre les écritures concurrentes sûres.
- **CI Semgrep rouge `investai-no-string-concatenated-sql`** (Semgrep ne tourne qu'en CI) : littéraux SQL
  concaténés par `+`, même entre constantes (`SELECT + "WHERE …"`) → une requête = un seul text
  block `"""…"""` (répéter la liste de colonnes) ; vérifier en local avec Docker :
  `docker run --rm -v "$PWD:/src" -w /src semgrep/semgrep semgrep scan --config .semgrep/ --error`.
- **Flyway « Found more than one migration with version N » en local** : `target/classes` garde
  une migration d'une autre branche (ou d'un stash) → `./mvnw clean` après un changement de
  branche qui touche `db/migration`.
- **Checkstyle `ClassFanOutComplexity` d'un test qui grossit** : un type nommé dans une
  déclaration (paramètre, variable locale, champ) compte, pas `var` ni l'accès à une constante
  (`TradeFileSegment.EQUITIES`) → `var`, fabriques de `FakeProviders`, helpers à paramètre
  `String`, ou une classe d'aide dédiée (`SyntheticParisFile`).
- **PMD `AvoidInstantiatingObjectsInLoops`** (tests compris) → construire la liste par
  `IntStream.range(...).mapToObj(...)`. **Error Prone `CanonicalDuration`** :
  `Duration.ofSeconds(120)` refusé → `Duration.ofMinutes(2)`.
- **Champ optionnel d'une API tierce qui fait échouer tout l'appel** : Jackson rejette la réponse
  entière si un champ typé (`BigDecimal`, `Long`) reçoit une valeur inattendue → lire un champ
  accessoire (ex. `volume` EODHD) en `String` et le convertir soi-même (nul si illisible).
- **`@RequestParam LocalDate`** : sans `@DateTimeFormat(iso = ISO.DATE)`, le format attendu dépend
  de la locale → toujours l'annoter. Une URL de test avec `/` final (`/instruments/?query=`) donne 404.

## Outils qualité (tous bloquants dans `./mvnw verify`)

Spotless (google-java-format), Checkstyle (`config/checkstyle`), Error Prone + NullAway (compilation,
warnings = erreurs), PMD (`config/pmd`), SpotBugs + FindSecBugs (`config/spotbugs`), ArchUnit,
JaCoCo (80 % lignes / 70 % branches), Maven Enforcer (Java 25, dépendances bannies).

Toute exclusion dans `config/**` doit être justifiée par un commentaire et validée par l'utilisateur.
