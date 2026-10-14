# Phase 1.2 — Erreurs RFC 9457, validation et logs JSON corrélés

## Contexte

Avant les premières API métier (Phase 2), le backend doit avoir **un seul format d'erreur** sur
lequel le frontend peut s'appuyer, et des logs exploitables par une machine (Grafana Loki en
Phase 3) où toutes les lignes d'une même requête se retrouvent. Décision : [ADR-0006](../adr/0006-contrat-erreurs-et-logs.md).

## Critères d'acceptation

1. **Given** une exception métier `ResourceNotFoundException` (resp. `BusinessRuleException`)
   levée par un cas d'usage, **when** l'API répond, **then** la réponse est un
   `application/problem+json` de statut 404 (resp. 422) contenant `status`, `title`, `instance`,
   le `code` stable de l'exception et le `requestId` — mais **pas** le message de l'exception.
2. **Given** un corps de requête invalide (Bean Validation sur un DTO `@Valid`) ou un paramètre
   invalide (`@RequestParam` / `@PathVariable` contraint), éventuellement combinés dans la même
   méthode, **when** l'API répond, **then** statut 400, `code = "validation.failed"` et une liste
   `errors` de `{ field, code }` : `field` est le champ du corps ou le nom du paramètre **tel que
   le client l'envoie** (`page-size`), `code` le nom de la contrainte (ex. `NotBlank`). La valeur
   rejetée n'est **jamais** renvoyée.
3. **Given** une erreur levée par Spring (JSON mal formé, paramètre de mauvais type, route
   inconnue, méthode non supportée), **then** la réponse est un problem+json avec `requestId` et
   un `code` générique dérivé du statut (`http.bad-request`, `http.not-found`,
   `http.method-not-allowed`…). Le `code` est donc **toujours** présent. Aucune réponse d'erreur
   ne contient de `detail` (ceux de Spring reprennent l'entrée du client ou des détails internes),
   et le backend ne sert aucune ressource statique.
4. **Given** une exception inattendue, **then** statut 500, `code = "internal.error"`, **aucun**
   message, classe ni stacktrace dans la réponse ; la stacktrace est loguée côté serveur avec le
   `requestId`.
5. **Given** une requête HTTP, **then** :
   - un identifiant de requête (UUID) est **généré par le serveur** ; un `X-Request-Id` envoyé
     par le client est ignoré (il ne peut ni forger ni dupliquer un identifiant de log) ;
   - il est renvoyé dans l'en-tête de réponse `X-Request-Id` et placé dans le MDC (`requestId`)
     pendant toute la requête, puis retiré ;
   - une ligne de log `INFO` résume la requête : méthode, **route** (motif, ex.
     `/api/v1/accounts/{id}`, jamais l'URL brute ni la query string), statut (500 si une
     exception sort de la chaîne de filtres), durée.
6. **Given** le profil par défaut (CI, tests, futur déploiement), **then** les logs console sont en
   JSON (format ECS) et contiennent le `requestId` ; **given** le profil `local`, ils restent en
   texte lisible, et toute valeur pouvant venir du client y est assainie (caractères de contrôle).
7. **Given** une erreur PostgreSQL (ex. violation d'unicité), **then** le message de l'exception,
   donc les logs, ne contient pas les valeurs des colonnes (`logServerErrorDetail=false`).

## Hors périmètre (repris dans les phases suivantes)

- **Phase 1.3** :
  - `ApiExceptionHandler` doit relancer `AccessDeniedException` / `AuthenticationException`
    (sinon le catch-all les transforme en 500 loguées en ERROR au lieu de 401/403) ; tests
    « anonyme → 401 », « `@PreAuthorize` refusé → 403 » ;
  - erreurs émises hors de Spring MVC (filtres de sécurité, pare-feu HTTP, `sendError`) : un
    `ErrorController` sur `/error` doit produire le même problem+json avec `requestId`.
- **Phase 2** : `ConstraintViolationException` (validation dans les services) → 400,
  `DataIntegrityViolationException` / `OptimisticLockingFailureException` → 409 avec un `code`
  stable ; contraintes croisées entre paramètres.
- **Phase 3** : traces OpenTelemetry (`traceId`), en complément du `requestId` ; exclure ou
  échantillonner les sondes `/actuator/health/*` dans le log de requêtes.
- **Phase 3.2** : propagation du MDC et ligne de synthèse correcte pour l'asynchrone (SSE).
- Traduction des codes d'erreur en messages français : frontend (Phase 1.5).
