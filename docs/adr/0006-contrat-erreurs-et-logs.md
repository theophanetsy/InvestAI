# 0006 — Contrat d'erreur de l'API et logs structurés

- **Statut** : Accepté
- **Date** : 2026-10-13

## Contexte

L'ADR-0004 fixe le format ProblemDetail (RFC 9457) sans préciser son contenu. Le frontend a besoin
d'un champ stable pour afficher un message en français, et le backend ne doit jamais divulguer de
détail interne (message d'exception, classe, stacktrace). Côté logs, il faut pouvoir retrouver
toutes les lignes d'une requête, et les envoyer plus tard vers Loki (Phase 3) sans parsing fragile.

## Options étudiées

- **Erreurs** : (a) ProblemDetail par défaut de Spring (`spring.mvc.problemdetails.enabled`) ;
  (b) ProblemDetail enrichi par un `@RestControllerAdvice` unique ; (c) format maison.
- **Identifiant de corrélation** : (a) `traceId` Micrometer Tracing / OpenTelemetry ; (b) filtre
  servlet `X-Request-Id`.
- **Format de logs** : (a) texte ; (b) JSON via le support natif de Spring Boot
  (`logging.structured.format.console`) en ECS ou Logstash ; (c) encodeur Logback tiers.

## Décision

- **Erreurs : (b)**. Un seul `ApiExceptionHandler` (étend `ResponseEntityExceptionHandler`).
  Extensions ajoutées au ProblemDetail :
  - `code` (toujours présent) : identifiant stable traduit par le frontend. Les exceptions
    métier (`DomainException`, package `shared.domain`, JDK uniquement) portent leur `code`
    (`account.not-found`) ; la validation donne `validation.failed`, une exception inattendue
    `internal.error`, et les erreurs levées par Spring un code dérivé du statut
    (`http.not-found`, `http.bad-request`…). Le message des exceptions sert aux logs, jamais à
    la réponse, et ne contient que des identifiants techniques non sensibles.
  - `errors` : `[{ field, code }]` pour la validation, sans la valeur rejetée.
  - `requestId` : pour relier un écran d'erreur aux logs.
  - Jamais de `detail` : ceux générés par Spring reprennent l'entrée du client ou des détails
    d'implémentation ; le frontend s'appuie sur `status` et `code`.
- **Corrélation : (b) maintenant**, (a) en complément en Phase 3.0. Le filtre est trivial, sans
  dépendance. L'identifiant est toujours généré par le serveur (un en-tête entrant serait une
  donnée client à valider) ; la corrélation entre services passera par le standard W3C
  `traceparent` d'OpenTelemetry.
- **Logs** : toute valeur pouvant venir du client passe par `LogSanitizer.oneLine` (caractères
  de contrôle et séparateurs de ligne neutralisés, car les logs texte du profil `local` ne les
  échappent pas) ; on logue la route (`/api/v1/accounts/{id}`), jamais l'URL brute ni la query
  string ; le pilote PostgreSQL n'inclut pas les valeurs des colonnes dans ses messages
  d'erreur (`logServerErrorDetail=false`).
- **Logs : (b) ECS** (Elastic Common Schema, proche des conventions OpenTelemetry), sans dépendance
  supplémentaire. JSON partout sauf profil `local`, où le texte reste plus lisible.

## Conséquences

- ➕ Contrat d'erreur unique, testable, documentable dans l'OpenAPI (Phase 1.4).
- ➕ Aucune fuite d'information interne ; une ligne de log par requête, sur la route et non l'URL.
- ➖ Les sorties console des tests sont en JSON (moins lisibles) ; `local` reste en texte.
- ➖ Le MDC est lié au thread : à propager explicitement pour l'asynchrone (SSE, Phase 3.2).
- ➖ Le catch-all `Exception` intercepte aussi les exceptions de sécurité levées dans les
  contrôleurs : la Phase 1.3 doit les relancer, et couvrir `/error` (erreurs hors Spring MVC).
- ⚠️ `instance` reprend le chemin de la requête : jamais de donnée sensible en variable de
  chemin.
